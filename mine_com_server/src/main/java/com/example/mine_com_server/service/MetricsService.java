package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.dto.response.MetricsResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.Metrics;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MetricsRepository;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private final MetricsRepository metricsRepository;
    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void collectAll() {
        List<MinecraftServer> activeServers = mcServerRepository.findAllByStatus("online");
        if (activeServers.isEmpty()) return;

        log.debug("[METRICS] Сбор метрик для {} серверов", activeServers.size());
        activeServers.forEach(mc -> {
            try {
                collectForServer(mc);
            } catch (Exception e) {
                log.warn("[METRICS] Ошибка сбора для {}: {}", mc.getName(), e.getMessage());
            }
        });
    }

    @Transactional
    public MetricsResponse collectForServer(MinecraftServer mc) {
        Server node = mc.getNode();

        BigDecimal cpuUsagePercent = parseCpu(node);

        String memRaw = sshService.execute(node,
                "free -m | awk '/^Mem:/{print $3\"/\"$2}'");
        int[] mem = parseMem(memRaw);
        BigDecimal ramUsagePercent = mem[1] > 0
                ? BigDecimal.valueOf(mem[0] * 100.0 / mem[1]).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String serverDataDir = mc.isDockerMode()
                ? remoteConfig.dockerServerDataDir(node, mc.getId(), mc.getStorageType())
                : remoteConfig.serverDir(node, mc.getId());

        String diskRaw = sshService.execute(node,
                "du -sm " + sshService.quote(serverDataDir) + " 2>/dev/null | cut -f1 || echo '0'");
        Integer diskUsedWorldMb = parseInt(diskRaw);

        String diskPercentRaw = sshService.execute(node,
                "df / | awk 'NR==2{print $5}' | tr -d '%'");
        BigDecimal diskUsagePercent = parseBigDecimal(diskPercentRaw);

        String uptimeRaw = sshService.execute(node,
                "awk '{print int($1)}' /proc/uptime 2>/dev/null || echo '0'");
        Long uptimeSeconds = parseLong(uptimeRaw);

        int playersOnline = getOnlinePlayers(mc, node);

        String backupDir = mc.isDockerMode()
                ? resolveDockerBackupDir(mc)
                : remoteConfig.vmBackupDir(node, mc.getId());

        String backupCountRaw = sshService.execute(node,
                "ls " + sshService.quote(backupDir) + "/*.tar.gz 2>/dev/null | wc -l || echo '0'");
        Integer totalBackups = parseInt(backupCountRaw);

        String backupSizeRaw = sshService.execute(node,
                "du -sm " + sshService.quote(backupDir) + " 2>/dev/null | cut -f1 || echo '0'");
        Integer backupsSizeMbTotal = parseInt(backupSizeRaw);

        Metrics metrics = Metrics.builder()
                .minecraftServer(mc)
                .recordedAt(LocalDateTime.now())
                .cpuUsagePercent(cpuUsagePercent)
                .ramUsagePercent(ramUsagePercent)
                .diskUsagePercent(diskUsagePercent)
                .diskUsedWorldMb(diskUsedWorldMb)
                .uptimeSeconds(uptimeSeconds)
                .playersOnline(playersOnline)
                .totalBackups(totalBackups)
                .backupsSizeMbTotal(backupsSizeMbTotal)
                .crashesLast24h((short) 0)
                .tps(null)
                .mspt(null)
                .chunksLoaded(null)
                .build();

        metricsRepository.save(metrics);
        return toResponse(metrics);
    }

    public MetricsResponse getLatest(UUID mcServerId) {
        return metricsRepository
                .findTopByMinecraftServerIdOrderByRecordedAtDesc(mcServerId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Метрики не найдены"));
    }

    public Page<MetricsResponse> getHistory(UUID mcServerId, int hours, int page, int size) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        Pageable pageable = PageRequest.of(page, size, Sort.by("recordedAt").descending());
        return metricsRepository
                .findAllByMinecraftServerIdAndRecordedAtAfter(mcServerId, from, pageable)
                .map(this::toResponse);
    }

    public Page<MetricsResponse> getByNode(UUID nodeId, int hours, int page, int size) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        Pageable pageable = PageRequest.of(page, size, Sort.by("recordedAt").descending());
        return metricsRepository
                .findAllByMinecraftServer_Node_IdAndRecordedAtAfter(nodeId, from, pageable)
                .map(this::toResponse);
    }

    @Transactional
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    public void cleanOldMetrics() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        metricsRepository.deleteOlderThan(threshold);
        log.info("[METRICS] Очистка метрик старше 7 дней выполнена");
    }

    private String resolveDockerBackupDir(MinecraftServer mc) {
        String override = mc.getBackupPath();
        if (override != null && !override.isBlank()) return override;
        return remoteConfig.dockerBackupDir(mc.getNode(), mc.getId(), mc.getStorageType());
    }

    private int getOnlinePlayers(MinecraftServer mc, Server node) {
        try {
            String logPath;
            String grepCmd;
            if (mc.isDockerMode()) {
                String container = DockerDeployService.dockerContainerName(mc.getId());
                grepCmd = "docker logs " + sshService.quote(container) + " 2>&1";
            } else {
                logPath = remoteConfig.serverDir(node, mc.getId()) + "/logs/latest.log";
                grepCmd = "cat " + sshService.quote(logPath) + " 2>/dev/null";
            }
            String joined = sshService.execute(node,
                    grepCmd + " | grep -c 'joined the game' 2>/dev/null || echo '0'");
            String left = sshService.execute(node,
                    grepCmd + " | grep -c 'left the game' 2>/dev/null || echo '0'");
            return Math.max(parseInt(joined) - parseInt(left), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal parseCpu(Server node) {
        try {
            String cmd =
                    "CPU1=$(cat /proc/stat | grep '^cpu ' | awk '{print $2+$3+$4+$5+$6+$7+$8}'); " +
                            "IDLE1=$(cat /proc/stat | grep '^cpu ' | awk '{print $5}'); " +
                            "sleep 1; " +
                            "CPU2=$(cat /proc/stat | grep '^cpu ' | awk '{print $2+$3+$4+$5+$6+$7+$8}'); " +
                            "IDLE2=$(cat /proc/stat | grep '^cpu ' | awk '{print $5}'); " +
                            "TOTAL=$((CPU2-CPU1)); IDLE=$((IDLE2-IDLE1)); " +
                            "echo \"scale=2; (($TOTAL-$IDLE)*100/$TOTAL)\" | bc";
            return parseBigDecimal(sshService.execute(node, cmd));
        } catch (Exception e) {
            log.warn("[METRICS] Ошибка парсинга CPU: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        try {
            return BigDecimal.valueOf(Double.parseDouble(raw.trim()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int[] parseMem(String raw) {
        try {
            String[] parts = raw.trim().split("/");
            return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (Exception e) { return new int[]{ 0, 0 }; }
    }

    private Integer parseInt(String raw) {
        try { return Integer.parseInt(raw.trim()); }
        catch (Exception e) { return 0; }
    }

    private Long parseLong(String raw) {
        try { return Long.parseLong(raw.trim()); }
        catch (Exception e) { return 0L; }
    }

    private MetricsResponse toResponse(Metrics m) {
        MetricsResponse r = new MetricsResponse();
        r.setId(m.getId());
        r.setMinecraftServerId(m.getMinecraftServer().getId());
        r.setRecordedAt(m.getRecordedAt());
        r.setCpuUsagePercent(m.getCpuUsagePercent());
        r.setRamUsagePercent(m.getRamUsagePercent());
        r.setDiskUsagePercent(m.getDiskUsagePercent());
        r.setDiskUsedWorldMb(m.getDiskUsedWorldMb());
        r.setUptimeSeconds(m.getUptimeSeconds());
        r.setPlayersOnline(m.getPlayersOnline());
        r.setTotalBackups(m.getTotalBackups());
        r.setBackupsSizeMbTotal(m.getBackupsSizeMbTotal());
        r.setCrashesLast24h(m.getCrashesLast24h());
        r.setTps(m.getTps());
        r.setMspt(m.getMspt());
        r.setChunksLoaded(m.getChunksLoaded());
        return r;
    }
}
