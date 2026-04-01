package com.example.mine_com_server.config;

import com.example.mine_com_server.model.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RemoteConfig {

    @Value("${app.remote.root:$HOME/mc-com}")
    private String remoteRoot;

    @Value("${app.remote.docker.ram-path:/dev/shm/mc-com/servers}")
    private String dockerRamPath;

    public String getRemoteRootTemplate() { return remoteRoot; }
    public String getDockerRamPath() { return dockerRamPath; }

    public String rootFor(Server server) { return resolveRemoteRoot(server); }
    public String scriptsDir(Server server) { return rootFor(server) + "/scripts"; }
    public String serversDir(Server server) { return rootFor(server) + "/servers"; }
    public String bundlesDir(Server server) { return rootFor(server) + "/dist/bundles"; }
    public String assetsDir(Server server) { return rootFor(server) + "/assets"; }

    public String dockerRoot(Server server) { return rootFor(server) + "/docker"; }
    public String dockerRuntimeDir(Server server) { return rootFor(server) + "/docker/runtime"; }
    public String dockerLogsDir(Server server) { return rootFor(server) + "/docker/logs"; }
    public String dockerSsdDir(Server server) { return rootFor(server) + "/docker/servers/ssd"; }
    public String dockerHddDir(Server server) { return rootFor(server) + "/docker/servers/hdd"; }
    public String backupSsdDir(Server server) { return rootFor(server) + "/docker/backups/ssd"; }
    public String backupHddDir(Server server) { return rootFor(server) + "/docker/backups/hdd"; }

    public String serverDir(Server server, UUID id) { return serversDir(server) + "/" + id; }
    public String runtimeDir(Server server, UUID id) { return serverDir(server, id) + "/runtime"; }
    public String vmBackupDir(Server server, UUID id) { return serverDir(server, id) + "/backups"; }

    public String scriptPath(Server server, String name) { return scriptsDir(server) + "/" + name; }

    public String dockerServerDataDir(Server server, UUID mcId, String storageType) {
        return resolveDockerStorageBase(server, storageType) + "/" + mcId;
    }

    public String dockerBackupDir(Server server, UUID mcId, String storageType) {
        String base = switch (normalizeStorageType(storageType)) {
            case "hdd", "ram" -> backupSsdDir(server);
            default -> backupHddDir(server);
        };
        return base + "/" + mcId;
    }

    private String resolveDockerStorageBase(Server server, String storageType) {
        return switch (normalizeStorageType(storageType)) {
            case "hdd" -> dockerHddDir(server);
            case "ram" -> dockerRamPath;
            default -> dockerSsdDir(server);
        };
    }

    private String normalizeStorageType(String storageType) {
        return storageType == null ? "ssd" : storageType.trim().toLowerCase();
    }

    private String resolveRemoteRoot(Server server) {
        String template = remoteRoot == null ? "" : remoteRoot.trim();
        if (template.isEmpty()) {
            template = "$HOME/mc-com";
        }

        String home = resolveHomeForServer(server);

        if (template.equals("~")) {
            return home;
        }
        if (template.startsWith("~/")) {
            return home + template.substring(1);
        }

        return template
                .replace("${HOME}", home)
                .replace("$HOME", home);
    }

    private String resolveHomeForServer(Server server) {
        String sshUser = server == null ? null : server.getSshUser();
        if (sshUser == null || sshUser.isBlank()) {
            throw new IllegalStateException("У ноды не задан sshUser, невозможно вычислить домашнюю директорию");
        }
        String user = sshUser.trim();
        return "root".equals(user) ? "/root" : "/home/" + user;
    }
}
