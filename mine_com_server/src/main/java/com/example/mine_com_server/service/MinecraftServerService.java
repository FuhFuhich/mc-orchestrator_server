package com.example.mine_com_server.service;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MinecraftServerService {

    private static final String SERVERS_DIR = "/home/sha/mc-com/servers";

    private final MinecraftServerRepository mcServerRepository;
    private final ServerRepository serverRepository;
    private final SshService sshService;

    // ===== ПОЛУЧИТЬ ВСЕ MC-СЕРВЕРЫ ПОЛЬЗОВАТЕЛЯ =====

    public List<MinecraftServerResponse> getAllByUser(UUID userId) {
        return mcServerRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ===== ПОЛУЧИТЬ ВСЕ MC-СЕРВЕРЫ НА НОДЕ =====

    public List<MinecraftServerResponse> getAllByNode(UUID nodeId) {
        return mcServerRepository.findAllByNodeId(nodeId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ===== ПОЛУЧИТЬ MC-СЕРВЕР ПО ID =====

    public MinecraftServerResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    // ===== СОЗДАТЬ MC-СЕРВЕР =====

    @Transactional
    public MinecraftServerResponse create(MinecraftServerRequest request) {
        Server node = serverRepository.findById(request.getNodeId())
                .orElseThrow(() -> new NotFoundException("Нода не найдена: " + request.getNodeId()));

        if (mcServerRepository.existsByNameAndNodeId(request.getName(), request.getNodeId())) {
            throw new IllegalStateException("Сервер с таким именем уже существует на этой ноде");
        }

        MinecraftServer mc = MinecraftServer.builder()
                .node(node)
                .name(request.getName())
                .minecraftVersion(request.getMinecraftVersion())
                .modLoader(request.getModLoader())
                .modLoaderVersion(request.getModLoaderVersion())
                .deployTarget(request.getDeployTarget())
                .status("offline")
                .gamePort(request.getGamePort() != null ? request.getGamePort() : 25565)
                .allocateAllResources(orDefault(request.getAllocateAllResources(), false))
                .ramMb(orDefault(request.getRamMb(), 2048))
                .cpuCores(orDefault(request.getCpuCores(), 2))
                .diskMb(request.getDiskMb())
                .autoRestart(orDefault(request.getAutoRestart(), true))
                .backupEnabled(orDefault(request.getBackupEnabled(), true))
                .backupIntervalHours(orDefault(request.getBackupIntervalHours(), 6))
                .backupAutoDelete(orDefault(request.getBackupAutoDelete(), true))
                .backupDeleteAfterHours(orDefault(request.getBackupDeleteAfterHours(), 168))
                .whitelistEnabled(orDefault(request.getWhitelistEnabled(), false))
                .rconEnabled(orDefault(request.getRconEnabled(), false))
                .rconPort(orDefault(request.getRconPort(), 25575))
                .rconPassword(request.getRconPassword())
                .build();

        mc = mcServerRepository.save(mc);
        log.info("MC-сервер создан: {} на ноде {}", mc.getName(), node.getIpAddress());
        return toResponse(mc);
    }

    // ===== ОБНОВИТЬ MC-СЕРВЕР =====

    @Transactional
    public MinecraftServerResponse update(UUID id, MinecraftServerRequest request) {
        MinecraftServer mc = findOrThrow(id);

        if (request.getName() != null) mc.setName(request.getName());
        if (request.getMinecraftVersion() != null) mc.setMinecraftVersion(request.getMinecraftVersion());
        if (request.getModLoader() != null) mc.setModLoader(request.getModLoader());
        if (request.getModLoaderVersion() != null) mc.setModLoaderVersion(request.getModLoaderVersion());
        if (request.getGamePort() != null) mc.setGamePort(request.getGamePort());
        if (request.getRamMb() != null) mc.setRamMb(request.getRamMb());
        if (request.getCpuCores() != null) mc.setCpuCores(request.getCpuCores());
        if (request.getDiskMb() != null) mc.setDiskMb(request.getDiskMb());
        if (request.getAutoRestart() != null) mc.setAutoRestart(request.getAutoRestart());
        if (request.getBackupEnabled() != null) mc.setBackupEnabled(request.getBackupEnabled());
        if (request.getBackupIntervalHours() != null) mc.setBackupIntervalHours(request.getBackupIntervalHours());
        if (request.getWhitelistEnabled() != null) mc.setWhitelistEnabled(request.getWhitelistEnabled());
        if (request.getRconEnabled() != null) mc.setRconEnabled(request.getRconEnabled());
        if (request.getRconPort() != null) mc.setRconPort(request.getRconPort());
        if (request.getRconPassword() != null) mc.setRconPassword(request.getRconPassword());

        return toResponse(mcServerRepository.save(mc));
    }

    // ===== УДАЛИТЬ MC-СЕРВЕР =====

    @Transactional
    public void delete(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        if ("online".equals(mc.getStatus())) {
            stopAsync(id);
        }
        mcServerRepository.delete(mc);
        log.info("MC-сервер удалён: {}", id);
    }

    // ===== ЗАПУСТИТЬ MC-СЕРВЕР =====

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<Void> startAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        try {
            mc.setStatus("starting");
            mcServerRepository.save(mc);

            sshService.execute(node, buildStartCommand(mc));

            mc.setStatus("online");
            mcServerRepository.save(mc);
            log.info("[START] MC-сервер запущен: {}", mc.getName());

        } catch (Exception e) {
            mc.setStatus("offline");
            mcServerRepository.save(mc);
            log.error("[START] Ошибка запуска {}: {}", mc.getName(), e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== ОСТАНОВИТЬ MC-СЕРВЕР =====

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<Void> stopAsync(UUID id) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        try {
            sshService.execute(node, buildStopCommand(mc));

            mc.setStatus("offline");
            mcServerRepository.save(mc);
            log.info("[STOP] MC-сервер остановлен: {}", mc.getName());

        } catch (Exception e) {
            log.error("[STOP] Ошибка остановки {}: {}", mc.getName(), e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== ПЕРЕЗАПУСТИТЬ MC-СЕРВЕР =====

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<Void> restartAsync(UUID id) {
        return stopAsync(id).thenCompose(v -> startAsync(id));
    }

    // ===== ПОЛУЧИТЬ ЛОГИ (последние N строк) =====

    public List<String> getConsoleLogs(UUID id, int lines) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        String logPath = SERVERS_DIR + "/" + mc.getId() + "/logs/latest.log";
        String cmd = String.format("tail -n %d %s 2>/dev/null || echo '[MC-COM] Лог файл не найден'", lines, logPath);

        String output = sshService.execute(node, cmd);
        return List.of(output.split("\n"));
    }

    // ===== ОТПРАВИТЬ КОМАНДУ В КОНСОЛЬ =====

    public void sendCommand(UUID id, String command) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        if (!"online".equals(mc.getStatus())) {
            throw new IllegalStateException("Сервер не запущен");
        }

        String screenName = "mc-" + mc.getId();
        String cmd = String.format("screen -S %s -X stuff '%s\n'", screenName, command);
        sshService.execute(node, cmd);
        log.info("[CONSOLE] Команда отправлена на {}: {}", mc.getName(), command);
    }

    // ===== СТРИМИНГ КОНСОЛИ ЧЕРЕЗ WEBSOCKET =====

    @Async("mc-async-")
    public CompletableFuture<Void> streamConsole(UUID id, Consumer<String> lineConsumer) {
        MinecraftServer mc = findOrThrow(id);
        Server node = mc.getNode();

        String logPath = SERVERS_DIR + "/" + mc.getId() + "/logs/latest.log";
        String cmd = "tail -f " + logPath;

        return sshService.streamLogs(node, id, cmd, lineConsumer);
    }

    // ===== КОМАНДЫ START / STOP =====

    private String buildStartCommand(MinecraftServer mc) {
        String serverDir = SERVERS_DIR + "/" + mc.getId();

        if ("docker".equals(mc.getDeployTarget())) {
            return String.format(
                    "docker start mc-%s 2>/dev/null || docker run -d --name mc-%s " +
                            "-p %d:%d -m %dm --cpus=%d " +
                            "-v %s:/data eclipse-temurin:21 " +
                            "java -Xmx%dM -Xms512M -jar /data/server.jar nogui",
                    mc.getId(), mc.getId(),
                    mc.getGamePort(), mc.getGamePort(),
                    mc.getRamMb(), mc.getCpuCores(),
                    serverDir,
                    mc.getRamMb()
            );
        }

        return String.format(
                "cd %s && screen -dmS mc-%s java -Xmx%dM -Xms512M -jar server.jar nogui",
                serverDir, mc.getId(), mc.getRamMb()
        );
    }

    private String buildStopCommand(MinecraftServer mc) {
        if ("docker".equals(mc.getDeployTarget())) {
            return "docker stop mc-" + mc.getId();
        }
        return "screen -S mc-" + mc.getId() + " -X stuff 'stop\n' && sleep 5";
    }

    // ===== УТИЛИТЫ =====

    public MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    private <T> T orDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    // Получить nodeId по mcServerId (для проверки доступа)
    public UUID getNodeId(UUID mcServerId) {
        return mcServerRepository.findById(mcServerId)
                .map(mc -> mc.getNode().getId())
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + mcServerId));
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
        r.setWhitelistEnabled(mc.getWhitelistEnabled());
        r.setRconEnabled(mc.getRconEnabled());
        r.setRconPort(mc.getRconPort());
        return r;
    }
}