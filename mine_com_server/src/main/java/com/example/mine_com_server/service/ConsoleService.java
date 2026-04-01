package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsoleService {

    private static final int DEFAULT_LINES  = 100;
    private static final int MAX_LINES      = 1000;

    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RemoteConfig remoteConfig;
    private final MinecraftServerService mcServerService;

    @Async("mc-async-")
    public CompletableFuture<Void> startStreaming(UUID mcServerId) {
        stopStreaming(mcServerId);

        MinecraftServer mc = findOrThrow(mcServerId);
        String cmd = buildTailCommand(mc);

        log.info("[CONSOLE] Начало стриминга для {}", mcServerId);
        return sshService.streamLogs(mc.getNode(), mcServerId, cmd,
                line -> broadcastLine(mcServerId, line));
    }

    public void stopStreaming(UUID mcServerId) {
        sshService.stopStream(mcServerId);
        log.debug("[CONSOLE] Стриминг {} остановлен", mcServerId);
    }

    public boolean isStreaming(UUID mcServerId) {
        return sshService.isStreaming(mcServerId);
    }

    public String[] getRecentLog(UUID mcServerId, int lines, int offset) {
        MinecraftServer mc = findOrThrow(mcServerId);
        int safeLines  = Math.max(1, Math.min(lines,  MAX_LINES));
        int safeOffset = Math.max(0, offset);
        int total      = safeLines + safeOffset;

        String raw;
        if (mc.isDockerMode()) {
            String containerName = DockerDeployService.dockerContainerName(mc.getId());
            raw = sshService.execute(mc.getNode(),
                    "docker logs --tail " + total + " " + sshService.quote(containerName)
                            + " 2>&1 || echo '[MC-COM] Контейнер не найден'");
        } else {
            String logPath = remoteConfig.serverDir(mc.getNode(), mc.getId()) + "/logs/latest.log";
            raw = sshService.execute(mc.getNode(),
                    "tail -n " + total + " " + sshService.quote(logPath)
                            + " 2>/dev/null | head -n " + safeLines
                            + " || echo '[MC-COM] Лог-файл не найден'");
        }

        String[] all = raw.split("\\R");
        if (safeOffset == 0) return all;

        // Apply offset: drop the last safeOffset lines
        int endIdx   = Math.max(0, all.length - safeOffset);
        int startIdx = Math.max(0, endIdx - safeLines);
        return Arrays.copyOfRange(all, startIdx, endIdx);
    }

    public List<String> getRecentLog(UUID mcServerId) {
        return Arrays.asList(getRecentLog(mcServerId, DEFAULT_LINES, 0));
    }

    public void sendCommand(UUID mcServerId, String command) {
        mcServerService.sendCommand(mcServerId, command);
    }

    private String buildTailCommand(MinecraftServer mc) {
        if (mc.isDockerMode()) {
            String containerName = DockerDeployService.dockerContainerName(mc.getId());
            return "docker logs --follow --tail 0 " + sshService.quote(containerName) + " 2>&1";
        }
        String logPath = remoteConfig.serverDir(mc.getNode(), mc.getId()) + "/logs/latest.log";
        return "tail -n 0 -F " + sshService.quote(logPath);
    }

    private void broadcastLine(UUID mcServerId, String line) {
        messagingTemplate.convertAndSend("/topic/console/" + mcServerId,
                Map.of("line", line, "ts", System.currentTimeMillis()));
    }

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }
}
