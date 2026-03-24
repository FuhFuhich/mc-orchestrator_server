package com.example.mine_com_server.service;

import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MetricsRepository;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchdogService {

    private final MinecraftServerRepository mcServerRepository;
    private final MetricsRepository metricsRepository;
    private final MinecraftServerService mcServerService;
    private final SshService sshService;

    @Scheduled(fixedDelay = 30_000)
    public void checkServers() {
        List<MinecraftServer> servers = mcServerRepository.findAllByStatus("online");
        if (servers.isEmpty()) return;

        log.debug("[WATCHDOG] Проверка {} серверов", servers.size());

        for (MinecraftServer mc : servers) {
            try {
                checkServer(mc);
            } catch (Exception e) {
                log.error("[WATCHDOG] Ошибка проверки {}: {}", mc.getName(), e.getMessage());
            }
        }
    }

    private void checkServer(MinecraftServer mc) {
        Server node = mc.getNode();

        String screenName = "mc-" + mc.getId();
        String result = sshService.execute(node,
                "screen -ls | grep " + screenName + " | wc -l");

        boolean alive = parseInt(result) > 0;

        if (alive) return;

        log.warn("[WATCHDOG] Сервер {} упал!", mc.getName());

        metricsRepository
                .findTopByMinecraftServerIdOrderByRecordedAtDesc(mc.getId())
                .ifPresent(metrics -> {
                    short crashes = metrics.getCrashesLast24h() != null
                            ? metrics.getCrashesLast24h() : 0;
                    metrics.setCrashesLast24h((short) (crashes + 1));
                    metricsRepository.save(metrics);
                });

        if (Boolean.TRUE.equals(mc.getAutoRestart())) {
            log.info("[WATCHDOG] Автоперезапуск: {}", mc.getName());
            mc.setStatus("starting");
            mcServerRepository.save(mc);
            mcServerService.startAsync(mc.getId());
        } else {
            log.info("[WATCHDOG] Автоперезапуск отключён для {}, статус → crashed", mc.getName());
            mc.setStatus("crashed");
            mcServerRepository.save(mc);
        }
    }

    private int parseInt(String raw) {
        try { return Integer.parseInt(raw.trim()); }
        catch (Exception e) { return 0; }
    }
}