package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
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
public class DockerDeployService {

    private static final List<String> DOCKER_SCRIPT_NAMES = List.of(
            "docker-common.sh",
            "docker-deploy.sh",
            "docker-start.sh",
            "docker-stop.sh",
            "docker-restart.sh",
            "docker-status.sh",
            "docker-cleanup.sh",
            "docker-backup.sh",
            "docker-restore.sh",
            "docker-logs.sh"
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
            prepareDockerNode(node);

            String containerName = dockerContainerName(mc.getId());

            sshService.runScript(
                    node,
                    remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "docker-deploy.sh"),
                    mc.getId().toString(),
                    mc.getMinecraftVersion(),
                    nvl(mc.getModLoader(), "paper"),
                    nvl(mc.getModLoaderVersion(), "latest"),
                    String.valueOf(orDefault(mc.getGamePort(), 25565)),
                    String.valueOf(orDefault(mc.getRamMb(), 2048)),
                    String.valueOf(orDefault(mc.getCpuCores(), 2)),
                    nvl(mc.getRconEnabled(), false).toString(),
                    String.valueOf(orDefault(mc.getRconPort(), 25575)),
                    nvl(mc.getRconPassword(), ""),
                    nvl(mc.getWhitelistEnabled(), false).toString(),
                    nvl(mc.getStorageType(), "ssd"),
                    nvl(mc.getBackupPath(), ""),
                    String.valueOf(orDefault(mc.getLogMaxFiles(), 10)),
                    String.valueOf(orDefault(mc.getBackupMaxCount(), 10)),
                    mc.getName()
            );

            assertDockerDeploySucceeded(node, mc.getId());

            mc = findOrThrow(mc.getId());
            mc.setDockerContainerId(containerName);
            mc.setStatus("offline");
            mcServerRepository.save(mc);

            log.info("[DOCKER-DEPLOY] Успешный деплой MC-сервера {} (container={})", mc.getId(), containerName);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            setStatus(mc.getId(), "error");
            log.error("[DOCKER-DEPLOY] Ошибка деплоя {}: {}", mc.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка Docker-деплоя: " + e.getMessage(), e);
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
            prepareDockerNode(node);
            sshService.runScript(node, remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "docker-cleanup.sh"), mc.getId().toString());

            mc = findOrThrow(mc.getId());
            mc.setDockerContainerId(null);
            mc.setStatus("offline");
            mcServerRepository.save(mc);

            log.info("[DOCKER-UNDEPLOY] Успешный cleanup MC-сервера {}", mc.getId());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            setStatus(mc.getId(), "error");
            log.error("[DOCKER-UNDEPLOY] Ошибка cleanup {}: {}", mc.getId(), e.getMessage(), e);
            throw new RuntimeException("Ошибка Docker-undeploy: " + e.getMessage(), e);
        }
    }

    private void prepareDockerNode(Server node) throws IOException {
        String root    = remoteConfig.rootFor(node);
        String scripts = remoteConfig.scriptsDir(node);
        String docker  = remoteConfig.dockerRoot(node);
        String runtime = remoteConfig.dockerRuntimeDir(node);

        sshService.execute(node,
                "mkdir -p " + sshService.quote(root) +
                        " " + sshService.quote(scripts) +
                        " " + sshService.quote(docker) +
                        " " + sshService.quote(runtime) +
                        " && chmod 755 " + sshService.quote(root) +
                        " " + sshService.quote(scripts) +
                        " " + sshService.quote(docker)
        );

        for (String scriptName : DOCKER_SCRIPT_NAMES) {
            uploadDockerScript(node, scriptName);
        }

        sshService.execute(node,
                "find " + sshService.quote(scripts) +
                        " -maxdepth 1 -type f -name 'docker-*.sh' -exec chmod 755 {} \\;"
        );
    }

    private void uploadDockerScript(Server node, String scriptName) throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
        if (!resource.exists()) {
            throw new IOException("Docker-скрипт не найден в resources/scripts: " + scriptName);
        }

        Path tmpFile = Files.createTempFile("mc-docker-script-", "-" + scriptName);
        try (InputStream in = resource.getInputStream()) {
            Files.write(tmpFile, in.readAllBytes());
            String remotePath = remoteConfig.scriptPath(node, scriptName);
            sshService.uploadFile(node, tmpFile.toString(), remotePath);
            sshService.execute(node, "chmod 755 " + sshService.quote(remotePath));
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    private void assertDockerDeploySucceeded(Server node, UUID mcServerId) {
        String stateFile  = remoteConfig.dockerRuntimeDir(node) + "/" + mcServerId + "/docker-state.env";
        String deployLog  = remoteConfig.dockerLogsDir(node)    + "/" + mcServerId + "/deploy.log";
        String deployLock = remoteConfig.dockerRuntimeDir(node) + "/" + mcServerId + "/deploy.lock";

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

    public static String dockerContainerName(UUID mcId) {
        return "mc-" + mcId.toString().replace("-", "");
    }

    private static String nvl(String v, String fallback)   { return v == null || v.isBlank() ? fallback : v; }
    private static Boolean nvl(Boolean v, Boolean fallback) { return v == null ? fallback : v; }
    private static <T> T orDefault(T v, T def)             { return v != null ? v : def; }
}
