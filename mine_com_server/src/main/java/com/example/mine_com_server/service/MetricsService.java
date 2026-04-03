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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private static final Pattern SIZE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]+)?");
    private static final Pattern PLAYER_COUNT_PATTERN = Pattern.compile("there are\\s+(\\d+)\\s+of\\s+(?:a\\s+)?max(?:imum)?\\s+\\d+\\s+players?\\s+online", Pattern.CASE_INSENSITIVE);

    private final MetricsRepository metricsRepository;
    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;
    private final RconService rconService;

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void collectAll() {
        List<MinecraftServer> activeServers = mcServerRepository.findAllByStatus("online");
        if (activeServers.isEmpty()) {
            return;
        }

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
        RuntimeSnapshot snapshot = captureRuntime(mc);

        Metrics metrics = Metrics.builder()
                .minecraftServer(mc)
                .recordedAt(LocalDateTime.now())
                .cpuUsagePercent(snapshot.cpuUsagePercent())
                .ramUsagePercent(snapshot.ramUsagePercent())
                .ramUsedMb(snapshot.ramUsedMb())
                .ramTotalMb(snapshot.ramTotalMb())
                .diskUsagePercent(snapshot.diskUsagePercent())
                .diskUsedWorldMb(snapshot.diskUsedMb())
                .diskTotalMb(snapshot.diskTotalMb())
                .uptimeSeconds(snapshot.uptimeSeconds())
                .playersOnline(snapshot.playersOnline())
                .totalBackups(snapshot.totalBackups())
                .backupsSizeMbTotal(snapshot.backupsSizeMbTotal())
                .networkRxMb(snapshot.networkRxMb())
                .networkTxMb(snapshot.networkTxMb())
                .containerRestarts(snapshot.containerRestarts())
                .crashesLast24h((short) 0)
                .tps(null)
                .mspt(null)
                .chunksLoaded(null)
                .build();

        Metrics saved = metricsRepository.save(metrics);
        return toResponse(saved, mc);
    }

    public MetricsResponse getRuntime(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        return toResponse(captureRuntime(mc), mc);
    }

    public MetricsResponse getLatest(UUID mcServerId) {
        Optional<Metrics> existing = metricsRepository.findTopByMinecraftServerIdOrderByRecordedAtDesc(mcServerId);
        if (existing.isPresent()) {
            Metrics metrics = existing.get();
            return toResponse(metrics, metrics.getMinecraftServer());
        }
        return getRuntime(mcServerId);
    }

    public Page<MetricsResponse> getHistory(UUID mcServerId, int hours, int page, int size) {
        LocalDateTime from = LocalDateTime.now().minusHours(Math.max(hours, 1));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by("recordedAt").descending());
        return metricsRepository
                .findAllByMinecraftServerIdAndRecordedAtAfter(mcServerId, from, pageable)
                .map(m -> toResponse(m, m.getMinecraftServer()));
    }

    public List<MetricsResponse> getSeries(UUID mcServerId, int hours, int points) {
        LocalDateTime from = LocalDateTime.now().minusHours(Math.max(hours, 1));
        List<Metrics> source = metricsRepository.findAllByMcServerIdAfter(mcServerId, from);
        if (source.isEmpty()) {
            return List.of();
        }

        List<Metrics> sampled = downsample(source, Math.max(points, 30));
        return sampled.stream()
                .map(m -> toResponse(m, m.getMinecraftServer()))
                .toList();
    }

    public Page<MetricsResponse> getByNode(UUID nodeId, int hours, int page, int size) {
        LocalDateTime from = LocalDateTime.now().minusHours(Math.max(hours, 1));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by("recordedAt").descending());
        return metricsRepository
                .findAllByMinecraftServer_Node_IdAndRecordedAtAfter(nodeId, from, pageable)
                .map(m -> toResponse(m, m.getMinecraftServer()));
    }

    @Transactional
    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000)
    public void cleanOldMetrics() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        metricsRepository.deleteOlderThan(threshold);
        log.info("[METRICS] Очистка метрик старше 7 дней выполнена");
    }

    public MinecraftServer findOrThrow(UUID mcServerId) {
        return mcServerRepository.findById(mcServerId)
                .orElseThrow(() -> new NotFoundException("Minecraft server не найден: " + mcServerId));
    }

    private List<Metrics> downsample(List<Metrics> source, int targetPoints) {
        if (source.size() <= targetPoints) {
            return source;
        }

        List<Metrics> sampled = new ArrayList<>(targetPoints);
        double step = (double) (source.size() - 1) / (double) (targetPoints - 1);
        for (int i = 0; i < targetPoints; i++) {
            int index = (int) Math.round(i * step);
            if (index >= source.size()) {
                index = source.size() - 1;
            }
            sampled.add(source.get(index));
        }
        return sampled;
    }

    private RuntimeSnapshot captureRuntime(MinecraftServer mc) {
        Server node = mc.getNode();
        String status = normalizeStatus(mc.getStatus());

        Integer diskUsedMb = parseInt(executeSafe(node,
                "du -sm " + sshService.quote(serverDataDir(mc)) + " 2>/dev/null | cut -f1 || echo 0"));
        Integer diskTotalMb = resolveDiskTotalMb(mc, node);
        BigDecimal diskUsagePercent = percent(diskUsedMb, diskTotalMb);

        Long uptimeSeconds = resolveServerUptimeSeconds(mc, node);

        Integer totalBackups = parseInt(executeSafe(node,
                "ls " + sshService.quote(backupDir(mc)) + "/*.tar.gz 2>/dev/null | wc -l || echo 0"));
        Integer backupsSizeMbTotal = parseInt(executeSafe(node,
                "du -sm " + sshService.quote(backupDir(mc)) + " 2>/dev/null | cut -f1 || echo 0"));

        int playersOnline = getOnlinePlayers(mc, node);
        Integer ramTotalMb = resolveConfiguredRamMb(mc, node);
        Integer ramUsedMb = 0;
        BigDecimal ramUsagePercent = BigDecimal.ZERO;
        BigDecimal cpuUsagePercent = BigDecimal.ZERO;
        BigDecimal networkRxMb = null;
        BigDecimal networkTxMb = null;
        Integer containerRestarts = null;

        if (mc.isDockerMode()) {
            String statsRaw = executeSafe(node,
                    "docker stats --no-stream --format '{{.CPUPerc}}|{{.MemUsage}}|{{.NetIO}}' " + sshService.quote(resolveContainerName(mc)) + " 2>/dev/null || echo '0%|0B / 0B|0B / 0B'");
            DockerStats dockerStats = parseDockerStats(statsRaw);
            cpuUsagePercent = dockerStats.cpuPercent();
            ramUsedMb = dockerStats.ramUsedMb();
            ramTotalMb = dockerStats.ramTotalMb() > 0 ? dockerStats.ramTotalMb() : ramTotalMb;
            ramUsagePercent = percent(ramUsedMb, ramTotalMb);
            networkRxMb = dockerStats.networkRxMb();
            networkTxMb = dockerStats.networkTxMb();
            containerRestarts = resolveContainerRestarts(mc, node);
        } else {
            String pidFile = remoteConfig.runtimeDir(node, mc.getId()) + "/server.pid";
            String procRaw = executeSafe(node,
                    "PID=$(cat " + sshService.quote(pidFile) + " 2>/dev/null || true); " +
                            "if [ -n \"$PID\" ] && kill -0 \"$PID\" 2>/dev/null; then " +
                            "ps -p \"$PID\" -o %cpu=,rss= --no-headers | awk '{print $1\"|\"$2}'; " +
                            "else echo '0|0'; fi");
            String[] parts = procRaw.trim().split("\\|");
            cpuUsagePercent = parts.length > 0 ? parseBigDecimal(parts[0].replace('%', ' ').trim()) : BigDecimal.ZERO;
            int rssKb = parts.length > 1 ? parseInt(parts[1]) : 0;
            ramUsedMb = (int) Math.max(0, Math.round(rssKb / 1024.0));
            ramUsagePercent = percent(ramUsedMb, ramTotalMb);
        }

        return new RuntimeSnapshot(
                status,
                mc.getStorageType(),
                cpuUsagePercent,
                ramUsedMb,
                ramTotalMb,
                ramUsagePercent,
                diskUsedMb,
                diskTotalMb,
                diskUsagePercent,
                uptimeSeconds,
                playersOnline,
                totalBackups,
                backupsSizeMbTotal,
                networkRxMb,
                networkTxMb,
                containerRestarts,
                LocalDateTime.now()
        );
    }

    private String serverDataDir(MinecraftServer mc) {
        return mc.isDockerMode()
                ? remoteConfig.dockerServerDataDir(mc.getNode(), mc.getId(), mc.getStorageType())
                : remoteConfig.serverDir(mc.getNode(), mc.getId());
    }

    private String backupDir(MinecraftServer mc) {
        if (mc.isDockerMode()) {
            String override = mc.getBackupPath();
            if (override != null && !override.isBlank()) {
                return override;
            }
            return remoteConfig.dockerBackupDir(mc.getNode(), mc.getId(), mc.getStorageType());
        }
        return remoteConfig.vmBackupDir(mc.getNode(), mc.getId());
    }

    private Integer resolveConfiguredRamMb(MinecraftServer mc, Server node) {
        if (mc.getRamMb() != null && mc.getRamMb() > 0) {
            return mc.getRamMb();
        }
        return parseInt(executeSafe(node, "free -m | awk '/^Mem:/{print $2}'"));
    }

    private Integer resolveDiskTotalMb(MinecraftServer mc, Server node) {
        if (mc.getDiskMb() != null && mc.getDiskMb() > 0) {
            return mc.getDiskMb();
        }
        return parseInt(executeSafe(node,
                "df -Pm " + sshService.quote(serverDataDir(mc)) + " | awk 'NR==2{print $2}'"));
    }

    private Long resolveServerUptimeSeconds(MinecraftServer mc, Server node) {
        if (mc.isDockerMode()) {
            String container = resolveContainerName(mc);
            String raw = executeSafe(node,
                    "START=$(docker inspect -f '{{.State.StartedAt}}|{{.State.Running}}' " + sshService.quote(container) + " 2>/dev/null || echo '|false'); " +
                            "RUNNING=$(echo \"$START\" | awk -F'|' '{print $2}'); " +
                            "STARTED=$(echo \"$START\" | awk -F'|' '{print $1}'); " +
                            "if [ \"$RUNNING\" = \"true\" ] && [ -n \"$STARTED\" ]; then NOW=$(date +%s); TS=$(date -d \"$STARTED\" +%s 2>/dev/null || echo 0); echo $((NOW-TS)); else echo 0; fi");
            return parseLong(raw);
        }

        String pidFile = remoteConfig.runtimeDir(node, mc.getId()) + "/server.pid";
        String raw = executeSafe(node,
                "PID=$(cat " + sshService.quote(pidFile) + " 2>/dev/null || true); " +
                        "if [ -n \"$PID\" ] && kill -0 \"$PID\" 2>/dev/null; then ps -p \"$PID\" -o etimes= --no-headers | tr -d ' '; else echo 0; fi");
        return parseLong(raw);
    }

    private Integer resolveContainerRestarts(MinecraftServer mc, Server node) {
        if (!mc.isDockerMode()) {
            return null;
        }
        String raw = executeSafe(node,
                "docker inspect -f '{{.RestartCount}}' " + sshService.quote(resolveContainerName(mc)) + " 2>/dev/null || echo 0");
        return parseInt(raw);
    }

    private String resolveContainerName(MinecraftServer mc) {
        if (mc.getDockerContainerId() != null && !mc.getDockerContainerId().isBlank()) {
            return mc.getDockerContainerId();
        }
        return DockerDeployService.dockerContainerName(mc.getId());
    }

    private int getOnlinePlayers(MinecraftServer mc, Server node) {
        Integer rconPlayers = getOnlinePlayersViaRcon(mc);
        if (rconPlayers != null) {
            return rconPlayers;
        }

        try {
            String grepCmd;
            if (mc.isDockerMode()) {
                String container = resolveContainerName(mc);
                grepCmd = "docker logs " + sshService.quote(container) + " 2>&1";
            } else {
                String logPath = remoteConfig.serverDir(node, mc.getId()) + "/logs/latest.log";
                grepCmd = "cat " + sshService.quote(logPath) + " 2>/dev/null";
            }
            String joined = sshService.execute(node,
                    grepCmd + " | grep -c 'joined the game' 2>/dev/null || echo 0");
            String left = sshService.execute(node,
                    grepCmd + " | grep -c 'left the game' 2>/dev/null || echo 0");
            return Math.max(parseInt(joined) - parseInt(left), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private Integer getOnlinePlayersViaRcon(MinecraftServer mc) {
        if (!Boolean.TRUE.equals(mc.getRconEnabled())) {
            return null;
        }
        try {
            String response = rconService.sendCommand(mc.getId(), "list");
            if (response == null || response.isBlank()) {
                return null;
            }
            Matcher matcher = PLAYER_COUNT_PATTERN.matcher(response.toLowerCase(Locale.ROOT));
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("[METRICS] RCON list fallback для {}: {}", mc.getName(), e.getMessage());
        }
        return null;
    }

    private DockerStats parseDockerStats(String raw) {
        try {
            String[] parts = raw.split("\\|", 3);
            BigDecimal cpu = parts.length > 0 ? parseBigDecimal(parts[0].replace("%", "")) : BigDecimal.ZERO;
            String memPart = parts.length > 1 ? parts[1] : "0B / 0B";
            String[] mem = memPart.split("/");
            int usedMb = mem.length > 0 ? sizeToMb(mem[0]) : 0;
            int totalMb = mem.length > 1 ? sizeToMb(mem[1]) : 0;

            String netPart = parts.length > 2 ? parts[2] : "0B / 0B";
            String[] net = netPart.split("/");
            BigDecimal rxMb = net.length > 0 ? sizeToBigDecimalMb(net[0]) : BigDecimal.ZERO;
            BigDecimal txMb = net.length > 1 ? sizeToBigDecimalMb(net[1]) : BigDecimal.ZERO;

            return new DockerStats(cpu, usedMb, totalMb, rxMb, txMb);
        } catch (Exception e) {
            return new DockerStats(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private int sizeToMb(String raw) {
        Matcher matcher = SIZE_PATTERN.matcher(raw == null ? "" : raw.trim());
        if (!matcher.find()) {
            return 0;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "B" : matcher.group(2).trim().toUpperCase(Locale.ROOT);
        double mb = switch (unit) {
            case "B" -> value / (1024d * 1024d);
            case "K", "KB", "KIB" -> value / 1024d;
            case "M", "MB", "MIB" -> value;
            case "G", "GB", "GIB" -> value * 1024d;
            case "T", "TB", "TIB" -> value * 1024d * 1024d;
            default -> value;
        };
        return (int) Math.max(0, Math.round(mb));
    }

    private BigDecimal sizeToBigDecimalMb(String raw) {
        Matcher matcher = SIZE_PATTERN.matcher(raw == null ? "" : raw.trim());
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "B" : matcher.group(2).trim().toUpperCase(Locale.ROOT);
        double mb = switch (unit) {
            case "B" -> value / (1024d * 1024d);
            case "K", "KB", "KIB" -> value / 1024d;
            case "M", "MB", "MIB" -> value;
            case "G", "GB", "GIB" -> value * 1024d;
            case "T", "TB", "TIB" -> value * 1024d * 1024d;
            default -> value;
        };
        return BigDecimal.valueOf(mb).setScale(2, RoundingMode.HALF_UP);
    }

    private MetricsResponse toResponse(Metrics metrics, MinecraftServer mc) {
        MetricsResponse response = new MetricsResponse();
        response.setId(metrics.getId());
        response.setMinecraftServerId(metrics.getMinecraftServer().getId());
        response.setRecordedAt(metrics.getRecordedAt());
        response.setCpuUsagePercent(metrics.getCpuUsagePercent());
        response.setRamUsagePercent(metrics.getRamUsagePercent());
        response.setRamUsedMb(metrics.getRamUsedMb());
        response.setRamTotalMb(metrics.getRamTotalMb());
        response.setDiskUsagePercent(metrics.getDiskUsagePercent());
        response.setDiskUsedWorldMb(metrics.getDiskUsedWorldMb());
        response.setDiskTotalMb(metrics.getDiskTotalMb());
        response.setUptimeSeconds(metrics.getUptimeSeconds());
        response.setPlayersOnline(metrics.getPlayersOnline());
        response.setTotalBackups(metrics.getTotalBackups());
        response.setBackupsSizeMbTotal(metrics.getBackupsSizeMbTotal());
        response.setNetworkRxMb(metrics.getNetworkRxMb());
        response.setNetworkTxMb(metrics.getNetworkTxMb());
        response.setContainerRestarts(metrics.getContainerRestarts());
        response.setCrashesLast24h(metrics.getCrashesLast24h());
        response.setStatus(normalizeStatus(mc.getStatus()));
        response.setStorageType(mc.getStorageType());
        response.setTps(metrics.getTps());
        response.setMspt(metrics.getMspt());
        response.setChunksLoaded(metrics.getChunksLoaded());
        return response;
    }

    private MetricsResponse toResponse(RuntimeSnapshot snapshot, MinecraftServer mc) {
        MetricsResponse response = new MetricsResponse();
        response.setMinecraftServerId(mc.getId());
        response.setRecordedAt(snapshot.collectedAt());
        response.setCpuUsagePercent(snapshot.cpuUsagePercent());
        response.setRamUsagePercent(snapshot.ramUsagePercent());
        response.setRamUsedMb(snapshot.ramUsedMb());
        response.setRamTotalMb(snapshot.ramTotalMb());
        response.setDiskUsagePercent(snapshot.diskUsagePercent());
        response.setDiskUsedWorldMb(snapshot.diskUsedMb());
        response.setDiskTotalMb(snapshot.diskTotalMb());
        response.setUptimeSeconds(snapshot.uptimeSeconds());
        response.setPlayersOnline(snapshot.playersOnline());
        response.setTotalBackups(snapshot.totalBackups());
        response.setBackupsSizeMbTotal(snapshot.backupsSizeMbTotal());
        response.setNetworkRxMb(snapshot.networkRxMb());
        response.setNetworkTxMb(snapshot.networkTxMb());
        response.setContainerRestarts(snapshot.containerRestarts());
        response.setCrashesLast24h((short) 0);
        response.setStatus(snapshot.status());
        response.setStorageType(snapshot.storageType());
        response.setTps(null);
        response.setMspt(null);
        response.setChunksLoaded(null);
        return response;
    }

    private String normalizeStatus(String rawStatus) {
        return rawStatus == null || rawStatus.isBlank() ? "offline" : rawStatus.trim().toLowerCase(Locale.ROOT);
    }

    private String executeSafe(Server node, String command) {
        try {
            return sshService.execute(node, command);
        } catch (Exception e) {
            log.warn("[METRICS] Ошибка выполнения команды '{}': {}", command, e.getMessage());
            return "0";
        }
    }

    private BigDecimal percent(Number used, Number total) {
        double totalValue = total == null ? 0d : total.doubleValue();
        double usedValue = used == null ? 0d : used.doubleValue();
        if (totalValue <= 0d) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((usedValue * 100d) / totalValue).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseBigDecimal(String raw) {
        try {
            return BigDecimal.valueOf(Double.parseDouble(raw.trim())).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private record DockerStats(
            BigDecimal cpuPercent,
            Integer ramUsedMb,
            Integer ramTotalMb,
            BigDecimal networkRxMb,
            BigDecimal networkTxMb
    ) {}

    private record RuntimeSnapshot(
            String status,
            String storageType,
            BigDecimal cpuUsagePercent,
            Integer ramUsedMb,
            Integer ramTotalMb,
            BigDecimal ramUsagePercent,
            Integer diskUsedMb,
            Integer diskTotalMb,
            BigDecimal diskUsagePercent,
            Long uptimeSeconds,
            Integer playersOnline,
            Integer totalBackups,
            Integer backupsSizeMbTotal,
            BigDecimal networkRxMb,
            BigDecimal networkTxMb,
            Integer containerRestarts,
            LocalDateTime collectedAt
    ) {}
}
