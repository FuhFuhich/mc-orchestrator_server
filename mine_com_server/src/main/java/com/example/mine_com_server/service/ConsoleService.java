package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsoleService {

    private static final String SERVERS_DIR = "/home/sha/mc-com/servers";

    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final SimpMessagingTemplate messagingTemplate;

    // ===== НАЧАТЬ СТРИМИНГ =====

    @Async("mc-async-")
    public CompletableFuture<Void> startStreaming(UUID mcServerId) {
        if (sshService.isStreaming(mcServerId)) {
            log.info("[CONSOLE] Стриминг для {} уже активен", mcServerId);
            return CompletableFuture.completedFuture(null);
        }

        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        String logPath = SERVERS_DIR + "/" + mcServerId + "/logs/latest.log";
        String cmd = "tail -f " + logPath;
        String topic = "/topic/console/" + mcServerId;

        log.info("[CONSOLE] Запуск стриминга для {}", mc.getName());

        return sshService.streamLogs(node, mcServerId, cmd, line ->
                messagingTemplate.convertAndSend(topic, line)
        );
    }

    // ===== ОСТАНОВИТЬ СТРИМИНГ =====

    public void stopStreaming(UUID mcServerId) {
        sshService.stopStream(mcServerId);
        log.info("[CONSOLE] Стриминг остановлен для {}", mcServerId);
    }

    // ===== ОТПРАВИТЬ КОМАНДУ =====

    public void sendCommand(UUID mcServerId, String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Команда не может быть пустой");
        }

        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        if (!"online".equals(mc.getStatus())) {
            throw new IllegalStateException("Сервер не запущен");
        }

        String screenName = "mc-" + mcServerId;
        String cmd = String.format("screen -S %s -X stuff '%s\n'", screenName, command);
        sshService.execute(node, cmd);
        log.info("[CONSOLE] Команда на {}: {}", mc.getName(), command);
    }

    // ===== ПОСЛЕДНИЕ N СТРОК =====

    public String[] getRecentLog(UUID mcServerId, int lines, int offset) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        String logPath = SERVERS_DIR + "/" + mcServerId + "/logs/latest.log";
        String cmd = String.format(
                "tail -n %d %s 2>/dev/null | head -n %d || echo '[MC-COM] Лог не найден'",
                lines + offset, logPath, lines
        );
        String output = sshService.execute(node, cmd);
        return output.split("\n");
    }

    // ===== СТАТУС СТРИМИНГА =====

    public boolean isStreaming(UUID mcServerId) {
        return sshService.isStreaming(mcServerId);
    }

    // ===== УТИЛИТЫ =====

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }
}