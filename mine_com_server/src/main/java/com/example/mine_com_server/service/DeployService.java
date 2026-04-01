package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployService {

    private static final List<String> SCRIPT_NAMES = List.of(
            "common.sh",
            "deploy.sh",
            "start.sh",
            "stop.sh",
            "restart.sh",
            "status.sh",
            "backup.sh",
            "restore.sh",
            "cleanup.sh",
            "install_java.sh"
    );

    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> deploy(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        setStatus(mc.getId(), "deploying");
        try {
            prepareNode(node);
            uploadBundle(node, mc);

            sshService.runScript(
                    node,
                    remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "deploy.sh"),
                    mc.getId().toString(),
                    mc.getMinecraftVersion(),
                    nvl(mc.getModLoader(), "vanilla"),
                    nvl(mc.getModLoaderVersion(), "latest"),
                    String.valueOf(mc.getGamePort()),
                    String.valueOf(mc.getRamMb()),
                    String.valueOf(mc.getCpuCores()),
                    nvl(mc.getRconEnabled(), false).toString(),
                    String.valueOf(mc.getRconPort()),
                    nvl(mc.getRconPassword(), ""),
                    nvl(mc.getWhitelistEnabled(), false).toString()
            );

            assertDeploySucceeded(node, mc.getId());
            setStatus(mc.getId(), "offline");
            log.info("[DEPLOY] Успешный деплой MC-сервера {}", mc.getId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            setStatus(mc.getId(), "error");
            log.error("[DEPLOY] Ошибка деплоя {}: {}", mc.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка деплоя: " + e.getMessage(), e);
        }
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> redeploy(UUID mcServerId) {
        return deploy(mcServerId);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> undeploy(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        setStatus(mc.getId(), "stopping");
        try {
            prepareNode(node);
            sshService.runScript(node, remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "cleanup.sh"), mc.getId().toString());
            setStatus(mc.getId(), "offline");
            log.info("[UNDEPLOY] Успешный cleanup MC-сервера {}", mc.getId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            setStatus(mc.getId(), "error");
            log.error("[UNDEPLOY] Ошибка undeploy {}: {}", mc.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка undeploy: " + e.getMessage(), e);
        }
    }

    private void prepareNode(Server node) throws IOException {
        String root    = remoteConfig.rootFor(node);
        String scripts = remoteConfig.scriptsDir(node);
        String servers = remoteConfig.serversDir(node);
        String bundles = remoteConfig.bundlesDir(node);

        sshService.execute(node,
                "mkdir -p " + sshService.quote(root) +
                        " " + sshService.quote(scripts) +
                        " " + sshService.quote(servers) +
                        " " + sshService.quote(bundles) +
                        " && chmod 755 " + sshService.quote(root) +
                        " " + sshService.quote(scripts)
        );

        for (String scriptName : SCRIPT_NAMES) {
            uploadScript(node, scriptName);
        }

        sshService.execute(node,
                "find " + sshService.quote(scripts) +
                        " -maxdepth 1 -type f -name '*.sh' -exec chmod 755 {} \\;"
        );
    }

    private void uploadBundle(Server node, MinecraftServer mc) throws IOException {
        String loader = nvl(mc.getModLoader(), "vanilla").trim().toLowerCase();
        if ("vanilla".equals(loader)) {
            return;
        }

        String resourcePath = resolveBundleResourcePath(loader, mc.getMinecraftVersion(),
                nvl(mc.getModLoaderVersion(), "latest"));
        Resource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IOException("Bundle не найден в resources: " + resourcePath);
        }

        String remotePath = resolveRemoteBundlePath(node, loader, mc.getMinecraftVersion(),
                nvl(mc.getModLoaderVersion(), "latest"));
        sshService.execute(node, "mkdir -p " + sshService.quote(remoteDir(remotePath)));

        Path tmpFile = Files.createTempFile("mc-bundle-", "-" + remoteFileName(remotePath));
        try (InputStream in = resource.getInputStream()) {
            Files.write(tmpFile, in.readAllBytes());
            sshService.uploadFile(node, tmpFile.toString(), remotePath);
            log.info("[DEPLOY] Bundle загружен на ноду: {}", remotePath);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    private String resolveBundleResourcePath(String loader, String mc, String lv) {
        return switch (loader) {
            case "forge"    -> "server-dist/bundles/forge/forge-" + mc + "-" + lv + ".tar.gz";
            case "paper"    -> "server-dist/bundles/paper/paper-" + mc + ".tar.gz";
            case "fabric"   -> "server-dist/bundles/fabric/fabric-" + mc + ".tar.gz";
            case "neoforge" -> "server-dist/bundles/neoforge/neoforge-" + lv + ".tar.gz";
            default -> throw new IllegalArgumentException("Неподдерживаемый loader для bundle: " + loader);
        };
    }

    private String resolveRemoteBundlePath(Server node, String loader, String mc, String lv) {
        String bd = remoteConfig.bundlesDir(node);
        return switch (loader) {
            case "forge"    -> bd + "/forge/forge-" + mc + "-" + lv + ".tar.gz";
            case "paper"    -> bd + "/paper/paper-" + mc + ".tar.gz";
            case "fabric"   -> bd + "/fabric/fabric-" + mc + ".tar.gz";
            case "neoforge" -> bd + "/neoforge/neoforge-" + lv + ".tar.gz";
            default -> throw new IllegalArgumentException("Неподдерживаемый loader для bundle: " + loader);
        };
    }

    private void uploadScript(Server node, String scriptName) throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
        if (!resource.exists()) {
            throw new IOException("Скрипт не найден в resources/scripts: " + scriptName);
        }

        Path tmpFile = Files.createTempFile("mc-script-", "-" + scriptName);
        try (InputStream in = resource.getInputStream()) {
            Files.write(tmpFile, in.readAllBytes());
            String remotePath = remoteConfig.scriptPath(node, scriptName);
            sshService.uploadFile(node, tmpFile.toString(), remotePath);
            sshService.execute(node, "chmod 755 " + sshService.quote(remotePath));
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    private void assertDeploySucceeded(Server node, UUID mcServerId) {
        String stateFile  = remoteConfig.runtimeDir(node, mcServerId) + "/state.env";
        String deployLog  = remoteConfig.serverDir(node, mcServerId)  + "/logs/deploy.log";
        String deployLock = remoteConfig.runtimeDir(node, mcServerId) + "/deploy.lock";

        String command =
                "if [ -f " + sshService.quote(stateFile) + " ]; then " +
                        "echo OK; " +
                        "elif [ -f " + sshService.quote(deployLock) + " ]; then " +
                        "echo DEPLOY_IN_PROGRESS; " +
                        "if [ -f " + sshService.quote(deployLog) + " ]; then tail -n 120 " + sshService.quote(deployLog) + "; else echo 'deploy.log not found'; fi; " +
                        "exit 12; " +
                        "else " +
                        "echo DEPLOY_NOT_COMPLETED; " +
                        "if [ -f " + sshService.quote(deployLog) + " ]; then tail -n 120 " + sshService.quote(deployLog) + "; else echo 'deploy.log not found'; fi; " +
                        "exit 11; " +
                        "fi";

        sshService.execute(node, command);
    }

    private void setStatus(UUID mcServerId, String status) {
        MinecraftServer entity = findOrThrow(mcServerId);
        entity.setStatus(status);
        mcServerRepository.save(entity);
    }

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Boolean nvl(Boolean value, Boolean fallback) {
        return value == null ? fallback : value;
    }

    private static String remoteDir(String path) {
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : ".";
    }

    private static String remoteFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
