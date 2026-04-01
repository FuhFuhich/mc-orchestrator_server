package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.dto.request.MinecraftServerRequest;
import com.example.mine_com_server.dto.response.MinecraftServerResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import com.example.mine_com_server.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MinecraftServerService {

    private static final Pattern SAFE_COMMAND =
            Pattern.compile("^[\\p{L}\\p{N}_:/.?=,+\\- ]{1,256}$");

    private final MinecraftServerRepository mcServerRepository;
    private final ServerRepository serverRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;
    private final DeployService deployService;
    private final DockerDeployService dockerDeployService;

    public List<MinecraftServerResponse> getAllByUser(UUID userId) {
        return mcServerRepository.findAllByUserId(userId).stream().map(this::toResponse).toList();
    }

    public List<MinecraftServerResponse> getAllByNode(UUID nodeId) {
        return mcServerRepository.findAllByNodeId(nodeId).stream().map(this::toResponse).toList();
    }

    public MinecraftServerResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public MinecraftServerResponse create(MinecraftServerRequest request) {
        Server node = serverRepository.findById(request.getNodeId())
                .orElseThrow(() -> new NotFoundException("Нода не найдена: " + request.getNodeId()));

        if (mcServerRepository.existsByNameAndNodeId(request.getName(), request.getNodeId())) {
            throw new IllegalStateException("Сервер с таким именем уже существует на этой ноде");
        }
        if (request.getGamePort() != null
                && mcServerRepository.existsByNodeIdAndGamePort(node.getId(), request.getGamePort())) {
            throw new IllegalStateException("Порт уже используется на этой ноде");
        }

        MinecraftServer mc = MinecraftServer.builder()
                .node(node)
                .name(request.getName().trim())
                .minecraftVersion(request.getMinecraftVersion().trim())
                .modLoader(normalize(request.getModLoader(), "paper"))
                .modLoaderVersion(blankToNull(request.getModLoaderVersion()))
                .deployTarget(normalize(request.getDeployTarget(), "screen"))
                .status("offline")
                .gamePort(orDefault(request.getGamePort(), 25565))
                .allocateAllResources(orDefault(request.getAllocateAllResources(), false))
                .ramMb(orDefault(request.getRamMb(), 2048))
                .cpuCores(orDefault(request.getCpuCores(), 2))
                .diskMb(orDefault(request.getDiskMb(), 20480))
                .autoRestart(orDefault(request.getAutoRestart(), true))
                .backupEnabled(orDefault(request.getBackupEnabled(), true))
                .backupIntervalHours(orDefault(request.getBackupIntervalHours(), 6))
                .backupAutoDelete(orDefault(request.getBackupAutoDelete(), true))
                .backupDeleteAfterHours(orDefault(request.getBackupDeleteAfterHours(), 168))
                .whitelistEnabled(orDefault(request.getWhitelistEnabled(), false))
                .rconEnabled(orDefault(request.getRconEnabled(), false))
                .rconPort(orDefault(request.getRconPort(), 25575))
                .rconPassword(Boolean.TRUE.equals(request.getRconEnabled()) ? request.getRconPassword() : null)
                .storageType(normalize(request.getStorageType(), "ssd"))
                .logMaxFiles(orDefault(request.getLogMaxFiles(), 10))
                .backupPath(blankToNull(request.getBackupPath()))
                .backupMaxCount(orDefault(request.getBackupMaxCount(), 10))
                .build();

        return toResponse(mcServerRepository.save(mc));
    }

    @Transactional
    public MinecraftServerResponse update(UUID id, MinecraftServerRequest request) {
        MinecraftServer mc = findOrThrow(id);

        if (request.getName() != null && !request.getName().equals(mc.getName())) {
            if (mcServerRepository.existsByNameAndNodeId(request.getName(), mc.getNode().getId())) {
                throw new IllegalStateException("Сервер с таким именем уже существует на этой ноде");
            }
            mc.setName(request.getName().trim());
        }
        if (request.getGamePort() != null && !request.getGamePort().equals(mc.getGamePort())) {
            if (mcServerRepository.existsByNodeIdAndGamePort(mc.getNode().getId(), request.getGamePort())) {
                throw new IllegalStateException("Порт уже используется на этой ноде");
            }
            mc.setGamePort(request.getGamePort());
        }
        if (request.getMinecraftVersion() != null)        mc.setMinecraftVersion(request.getMinecraftVersion().trim());
        if (request.getModLoader() != null)               mc.setModLoader(normalize(request.getModLoader(), mc.getModLoader()));
        if (request.getModLoaderVersion() != null)        mc.setModLoaderVersion(blankToNull(request.getModLoaderVersion()));
        if (request.getDeployTarget() != null)            mc.setDeployTarget(normalize(request.getDeployTarget(), mc.getDeployTarget()));
        if (request.getRamMb() != null)                   mc.setRamMb(request.getRamMb());
        if (request.getCpuCores() != null)                mc.setCpuCores(request.getCpuCores());
        if (request.getDiskMb() != null)                  mc.setDiskMb(request.getDiskMb());
        if (request.getAllocateAllResources() != null)     mc.setAllocateAllResources(request.getAllocateAllResources());
        if (request.getAutoRestart() != null)             mc.setAutoRestart(request.getAutoRestart());
        if (request.getBackupEnabled() != null)           mc.setBackupEnabled(request.getBackupEnabled());
        if (request.getBackupIntervalHours() != null)     mc.setBackupIntervalHours(request.getBackupIntervalHours());
        if (request.getBackupAutoDelete() != null)        mc.setBackupAutoDelete(request.getBackupAutoDelete());
        if (request.getBackupDeleteAfterHours() != null)  mc.setBackupDeleteAfterHours(request.getBackupDeleteAfterHours());
        if (request.getWhitelistEnabled() != null)        mc.setWhitelistEnabled(request.getWhitelistEnabled());
        if (request.getRconEnabled() != null)             mc.setRconEnabled(request.getRconEnabled());
        if (request.getRconPort() != null)                mc.setRconPort(request.getRconPort());
        if (request.getRconPassword() != null)            mc.setRconPassword(request.getRconPassword());
        if (request.getStorageType() != null)             mc.setStorageType(normalize(request.getStorageType(), mc.getStorageType()));
        if (request.getLogMaxFiles() != null)             mc.setLogMaxFiles(request.getLogMaxFiles());
        if (request.getBackupPath() != null)              mc.setBackupPath(blankToNull(request.getBackupPath()));
        if (request.getBackupMaxCount() != null)          mc.setBackupMaxCount(request.getBackupMaxCount());

        return toResponse(mcServerRepository.save(mc));
    }

    @Transactional
    public void delete(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        mcServerRepository.delete(mc);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> startAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        mc.setStatus("starting");
        mcServerRepository.save(mc);

        try {
            if (mc.isDockerMode()) {
                sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                        remoteConfig.scriptPath(mc.getNode(), "docker-start.sh"), mc.getId().toString());
            } else {
                awaitDeploymentReady(mc, Duration.ofMinutes(20));
                sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                        remoteConfig.scriptPath(mc.getNode(), "start.sh"), mc.getId().toString());
            }
            mc = findOrThrow(id);
            mc.setStatus("online");
        } catch (DeployInProgressException e) {
            mc = findOrThrow(id);
            mc.setStatus("deploying");
            log.warn("[START] Запуск {} отложен: {}", mc.getName(), e.getMessage());
        } catch (Exception e) {
            mc = findOrThrow(id);
            mc.setStatus("error");
            log.error("[START] Ошибка запуска {}: {}", mc.getName(), e.getMessage(), e);
        }

        mcServerRepository.save(mc);
        return CompletableFuture.completedFuture(null);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> stopAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        mc.setStatus("stopping");
        mcServerRepository.save(mc);

        try {
            stopSync(mc);
            mc = findOrThrow(id);
            mc.setStatus("offline");
        } catch (Exception e) {
            mc = findOrThrow(id);
            mc.setStatus("error");
            log.error("[STOP] Ошибка остановки {}: {}", mc.getName(), e.getMessage(), e);
        }

        mcServerRepository.save(mc);
        return CompletableFuture.completedFuture(null);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> restartAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        mc.setStatus("restarting");
        mcServerRepository.save(mc);

        try {
            if (mc.isDockerMode()) {
                sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                        remoteConfig.scriptPath(mc.getNode(), "docker-restart.sh"), mc.getId().toString());
            } else {
                awaitDeploymentReady(mc, Duration.ofMinutes(20));
                sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                        remoteConfig.scriptPath(mc.getNode(), "restart.sh"), mc.getId().toString());
            }
            mc = findOrThrow(id);
            mc.setStatus("online");
        } catch (DeployInProgressException e) {
            mc = findOrThrow(id);
            mc.setStatus("deploying");
            log.warn("[RESTART] Рестарт {} отложен: {}", mc.getName(), e.getMessage());
        } catch (Exception e) {
            mc = findOrThrow(id);
            mc.setStatus("error");
            log.error("[RESTART] Ошибка рестарта {}: {}", mc.getName(), e.getMessage(), e);
        }

        mcServerRepository.save(mc);
        return CompletableFuture.completedFuture(null);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> redeployAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);

        try {
            String status = mc.getStatus();
            if ("online".equalsIgnoreCase(status)
                    || "starting".equalsIgnoreCase(status)
                    || "restarting".equalsIgnoreCase(status)) {
                log.info("[REDEPLOY] Stopping server {} before redeploy", mc.getId());
                stopSync(mc);
            }
        } catch (Exception e) {
            log.warn("[REDEPLOY] Could not stop server {} before redeploy (continuing): {}",
                    mc.getId(), e.getMessage());
        }

        if (mc.isDockerMode()) {
            return dockerDeployService.deploy(id);
        } else {
            return deployService.deploy(id);
        }
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> deleteFullyAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        setStatus(id, "stopping");
        try {
            runCleanupScript(mc, node);
        } catch (Exception e) {
            log.warn("[DELETE-FULL] Cleanup script failed for {} (removing DB record anyway): {}",
                    id, e.getMessage());
        }

        mcServerRepository.deleteById(id);
        log.info("[DELETE-FULL] Сервер {} удалён из БД и с устройства", id);
        return CompletableFuture.completedFuture(null);
    }

    @Async("mc-async-")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<Void> deleteFromDeviceAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        setStatus(id, "stopping");
        try {
            runCleanupScript(mc, node);
        } catch (Exception e) {
            log.warn("[DELETE-DEVICE] Cleanup script failed for {}: {}", id, e.getMessage());
        }

        MinecraftServer updated = findOrThrow(id);
        updated.setStatus("undeployed");
        updated.setDockerContainerId(null);
        mcServerRepository.save(updated);

        log.info("[DELETE-DEVICE] Сервер {} удалён с устройства, запись в БД сохранена", id);
        return CompletableFuture.completedFuture(null);
    }

    public List<String> getConsoleLogs(UUID id, int lines) {
        MinecraftServer mc = findOrThrow(id);
        int safeLines = Math.max(1, Math.min(lines, 1000));
        String output;

        if (mc.isDockerMode()) {
            String containerName = DockerDeployService.dockerContainerName(mc.getId());
            output = sshService.execute(mc.getNode(),
                    "docker logs --tail " + safeLines + " " + sshService.quote(containerName)
                            + " 2>&1 || echo '[MC-COM] Контейнер не найден или не запущен'");
        } else {
            String logPath = remoteConfig.serverDir(mc.getNode(), mc.getId()) + "/logs/latest.log";
            output = sshService.execute(mc.getNode(),
                    "tail -n " + safeLines + " " + sshService.quote(logPath)
                            + " 2>/dev/null || echo '[MC-COM] Лог файл не найден'");
        }
        return List.of(output.split("\\R"));
    }

    public void sendCommand(UUID id, String command) {
        MinecraftServer mc = findOrThrow(id);
        if (!"online".equalsIgnoreCase(mc.getStatus())) {
            throw new IllegalStateException("Сервер не запущен");
        }

        String normalized = sanitizeConsoleCommand(command);

        if (mc.isDockerMode()) {
            if (!Boolean.TRUE.equals(mc.getRconEnabled())) {
                throw new IllegalStateException(
                        "Для Docker-серверов отправка команд доступна только через RCON. " +
                                "Включите RCON при создании сервера.");
            }
            throw new UnsupportedOperationException("DOCKER_RCON_REQUIRED:" + mc.getId());
        }

        // VM (screen) mode
        byte[] payload = (normalized + "\n").getBytes();
        String remoteTmpDir = remoteConfig.runtimeDir(mc.getNode(), mc.getId());
        String remoteTmpFile = remoteTmpDir + "/cmd-" + System.currentTimeMillis() + ".txt";

        sshService.execute(mc.getNode(), "mkdir -p " + sshService.quote(remoteTmpDir));
        sshService.uploadBytes(mc.getNode(), payload, remoteTmpFile);
        sshService.execute(mc.getNode(),
                "screen -S mc-" + mc.getId() + " -X readbuf " + sshService.quote(remoteTmpFile) +
                        " && screen -S mc-" + mc.getId() + " -X paste . && rm -f " + sshService.quote(remoteTmpFile)
        );
    }

    public String uploadArchive(UUID id, MultipartFile file, String type) {
        MinecraftServer mc = findOrThrow(id);
        validateArchive(file);

        String normalizedType = normalize(type, "modpack");
        String safeType = switch (normalizedType) {
            case "mods", "plugins", "world", "modpack" -> normalizedType;
            default -> throw new IllegalArgumentException("Допустимые типы архива: modpack, mods, plugins, world");
        };

        String remoteDir  = remoteConfig.assetsDir(mc.getNode()) + "/" + mc.getId() + "/uploads";
        String remoteFile = remoteDir + "/" + safeType + "-" + System.currentTimeMillis() + ".zip";

        try {
            sshService.execute(mc.getNode(), "mkdir -p " + sshService.quote(remoteDir));
            sshService.uploadBytes(mc.getNode(), file.getBytes(), remoteFile);
            return remoteFile;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать архив: " + e.getMessage(), e);
        }
    }

    public MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    public UUID getNodeId(UUID mcServerId) {
        return findOrThrow(mcServerId).getNode().getId();
    }

    public MinecraftServerResponse toResponse(MinecraftServer mc) {
        MinecraftServerResponse r = new MinecraftServerResponse();
        r.setId(mc.getId());
        r.setNodeId(mc.getNode().getId());
        r.setNodeName(mc.getNode().getName());
        r.setName(mc.getName());
        r.setMinecraftVersion(mc.getMinecraftVersion());
        r.setModLoader(mc.getModLoader());
        r.setModLoaderVersion(mc.getModLoaderVersion());
        r.setDeployTarget(mc.getDeployTarget());
        r.setStatus(mc.getStatus());
        r.setGamePort(mc.getGamePort());
        r.setCreatedAt(mc.getCreatedAt());
        r.setUpdatedAt(mc.getUpdatedAt());
        r.setRamMb(mc.getRamMb());
        r.setCpuCores(mc.getCpuCores());
        r.setDiskMb(mc.getDiskMb());
        r.setAutoRestart(mc.getAutoRestart());
        r.setBackupEnabled(mc.getBackupEnabled());
        r.setBackupIntervalHours(mc.getBackupIntervalHours());
        r.setBackupAutoDelete(mc.getBackupAutoDelete());
        r.setBackupDeleteAfterHours(mc.getBackupDeleteAfterHours());
        r.setWhitelistEnabled(mc.getWhitelistEnabled());
        r.setRconEnabled(mc.getRconEnabled());
        r.setRconPort(mc.getRconPort());
        r.setRemoteRoot(mc.isDockerMode()
                ? remoteConfig.dockerServerDataDir(mc.getNode(), mc.getId(), mc.getStorageType())
                : remoteConfig.serverDir(mc.getNode(), mc.getId()));
        r.setStorageType(mc.getStorageType());
        r.setDockerContainerId(mc.getDockerContainerId());
        r.setLogMaxFiles(mc.getLogMaxFiles());
        r.setBackupMaxCount(mc.getBackupMaxCount());
        return r;
    }

    private void stopSync(MinecraftServer mc) {
        if (mc.isDockerMode()) {
            sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                    remoteConfig.scriptPath(mc.getNode(), "docker-stop.sh"), mc.getId().toString());
        } else {
            sshService.runScript(mc.getNode(), remoteConfig.rootFor(mc.getNode()),
                    remoteConfig.scriptPath(mc.getNode(), "stop.sh"), mc.getId().toString());
        }
    }

    private void runCleanupScript(MinecraftServer mc, Server node) {
        if (mc.isDockerMode()) {
            sshService.runScript(node, remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "docker-cleanup.sh"), mc.getId().toString());
        } else {
            sshService.runScript(node, remoteConfig.rootFor(node),
                    remoteConfig.scriptPath(node, "cleanup.sh"), mc.getId().toString());
        }
    }

    private void awaitDeploymentReady(MinecraftServer mc, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        DeploymentProbe lastProbe = null;

        while (System.currentTimeMillis() < deadline) {
            lastProbe = probeDeployment(mc);
            switch (lastProbe.state()) {
                case READY    -> { return; }
                case DEPLOYING -> sleepQuietly(3000L);
                case FAILED   -> throw new IllegalStateException("Деплой завершился с ошибкой: " + lastProbe.details());
                case MISSING  -> throw new IllegalStateException("Деплой не был выполнен: " + lastProbe.details());
            }
        }

        if (lastProbe != null && lastProbe.state() == DeploymentState.DEPLOYING) {
            throw new DeployInProgressException("Деплой ещё выполняется. Попробуй чуть позже. " + lastProbe.details());
        }
        throw new IllegalStateException("Не удалось дождаться завершения деплоя");
    }

    private DeploymentProbe probeDeployment(MinecraftServer mc) {
        String runtimeDir = remoteConfig.runtimeDir(mc.getNode(), mc.getId());
        String stateFile = runtimeDir + "/state.env";
        String lockFile  = runtimeDir + "/deploy.lock";
        String deployLog = remoteConfig.serverDir(mc.getNode(), mc.getId())  + "/logs/deploy.log";

        String command =
                "STATE_FILE=" + sshService.quote(stateFile) + "; " +
                        "LOCK_FILE="  + sshService.quote(lockFile)  + "; " +
                        "LOG_FILE="   + sshService.quote(deployLog) + "; " +
                        "if [ -f \"$STATE_FILE\" ]; then echo '__STATE__:READY'; " +
                        "elif [ -f \"$LOCK_FILE\" ]; then echo '__STATE__:DEPLOYING'; " +
                        "elif [ -f \"$LOG_FILE\" ]; then echo '__STATE__:FAILED'; tail -n 80 \"$LOG_FILE\"; " +
                        "else echo '__STATE__:MISSING'; echo 'state.env and deploy.log not found'; fi";

        String output = sshService.execute(mc.getNode(), command);
        String[] lines = output.split("\\R", 2);
        String firstLine = lines.length > 0 ? lines[0].trim() : "__STATE__:MISSING";
        String details   = lines.length > 1 ? lines[1].trim() : "";

        DeploymentState state = switch (firstLine) {
            case "__STATE__:READY"     -> DeploymentState.READY;
            case "__STATE__:DEPLOYING" -> DeploymentState.DEPLOYING;
            case "__STATE__:FAILED"    -> DeploymentState.FAILED;
            default                    -> DeploymentState.MISSING;
        };
        return new DeploymentProbe(state, details);
    }

    private void setStatus(UUID id, String status) {
        findOrThrow(id).setStatus(status);
        mcServerRepository.save(findOrThrow(id));
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание деплоя было прервано", e);
        }
    }

    private void validateArchive(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Архив не передан");
        String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fn.endsWith(".zip")) throw new IllegalArgumentException("Поддерживаются только ZIP архивы");
    }

    private String sanitizeConsoleCommand(String command) {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("Команда не может быть пустой");
        String normalized = command.replace("\r", " ").replace("\n", " ").trim();
        if (!SAFE_COMMAND.matcher(normalized).matches())
            throw new IllegalArgumentException("Команда содержит недопустимые символы");
        return normalized;
    }

    private String normalize(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private <T> T orDefault(T value, T def) { return value != null ? value : def; }

    private enum DeploymentState { READY, DEPLOYING, FAILED, MISSING }
    private record DeploymentProbe(DeploymentState state, String details) {}
    private static class DeployInProgressException extends RuntimeException {
        DeployInProgressException(String msg) { super(msg); }
    }
}
