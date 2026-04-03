package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.dto.response.NodeUsageResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.NodeHardware;
import com.example.mine_com_server.model.NodeHardware.DiskInfo;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.NodeHardwareRepository;
import com.example.mine_com_server.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HardwareService {

    private final ServerRepository serverRepository;
    private final NodeHardwareRepository hardwareRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;

    public NodeHardware scanAndSave(UUID nodeId) {
        Server server = serverRepository.findById(nodeId)
                .orElseThrow(() -> new NotFoundException("Сервер не найден: " + nodeId));

        log.info("[HARDWARE] Сканирование ноды {}", server.getIpAddress());

        NodeHardware hw = hardwareRepository.findByNodeId(nodeId)
                .orElse(NodeHardware.builder().node(server).build());

        hw.setCpuModel(parseCpuModel(server));
        hw.setCpuCores(parseCpuCores(server));
        hw.setCpuThreads(parseCpuThreads(server));
        hw.setCpuMhz(parseCpuMhz(server));
        hw.setRamTotalMb(parseRamTotal(server));
        hw.setRamAvailableMb(parseRamAvailable(server));
        hw.setDisks(parseDisks(server));
        hw.setOsName(parseOsName(server));
        hw.setOsVersion(parseOsVersion(server));
        hw.setKernel(parseKernel(server));
        hw.setGpuModel(parseGpu(server));
        hw.setScannedAt(LocalDateTime.now());

        NodeHardware saved = hardwareRepository.save(hw);
        log.info("[HARDWARE] Сканирование завершено для {}", server.getIpAddress());
        return saved;
    }

    public NodeHardware getByNodeId(UUID nodeId) {
        return hardwareRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new NotFoundException("Hardware не найден для ноды: " + nodeId));
    }

    public NodeUsageResponse getLiveUsage(UUID nodeId) {
        Server server = serverRepository.findById(nodeId)
                .orElseThrow(() -> new NotFoundException("Сервер не найден: " + nodeId));

        NodeUsageResponse response = new NodeUsageResponse();
        response.setNodeId(server.getId());
        response.setNodeName(server.getName());
        response.setCollectedAt(LocalDateTime.now());
        response.setCpuUsagePercent(parseCpuUsage(server));

        BigDecimal[] loadAverage = parseLoadAverage(server);
        response.setCpuLoadAverage1m(loadAverage[0]);
        response.setCpuLoadAverage5m(loadAverage[1]);
        response.setCpuLoadAverage15m(loadAverage[2]);

        int[] ram = parseUsedTotal(server, "free -m | awk '/^Mem:/{print $3\"/\"$2}'");
        response.setRamUsedMb(ram[0]);
        response.setRamTotalMb(ram[1]);
        response.setRamUsagePercent(percent(ram[0], ram[1]));

        String mountTarget = remoteConfig.rootFor(server);
        int[] disk = parseUsedTotal(server,
                "df -Pm " + sshService.quote(mountTarget) + " | awk 'NR==2{print $3\"/\"$2}'");
        response.setDiskUsedMb(disk[0]);
        response.setDiskTotalMb(disk[1]);
        response.setDiskUsagePercent(percent(disk[0], disk[1]));

        BigDecimal[] network = parseNetworkTotals(server);
        response.setNetworkRxMb(network[0]);
        response.setNetworkTxMb(network[1]);

        response.setDockerContainersRunning(parseIntSafe(executeSafe(server, "docker ps -q 2>/dev/null | wc -l || echo 0")));
        response.setDockerContainersTotal(parseIntSafe(executeSafe(server, "docker ps -aq 2>/dev/null | wc -l || echo 0")));
        return response;
    }

    private String parseCpuModel(Server server) {
        try {
            return sshService.execute(server,
                    "lscpu | grep 'Model name' | sed 's/Model name:[ \\t]*//'");
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга CPU model: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseCpuCores(Server server) {
        try {
            String out = sshService.execute(server,
                    "lscpu | grep '^Core(s) per socket' | awk '{print $NF}'");
            return Integer.parseInt(out.trim());
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга CPU cores: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseCpuThreads(Server server) {
        try {
            String out = sshService.execute(server,
                    "lscpu | grep '^CPU(s):' | awk '{print $NF}'");
            return Integer.parseInt(out.trim());
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга CPU threads: {}", e.getMessage());
            return null;
        }
    }

    private Double parseCpuMhz(Server server) {
        try {
            String out = sshService.execute(server,
                    "lscpu | grep 'CPU MHz' | awk '{print $NF}'");
            return Double.parseDouble(out.trim());
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга CPU MHz: {}", e.getMessage());
            return null;
        }
    }

    private Long parseRamTotal(Server server) {
        try {
            String out = sshService.execute(server,
                    "free -m | grep Mem | awk '{print $2}'");
            return Long.parseLong(out.trim());
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга RAM total: {}", e.getMessage());
            return null;
        }
    }

    private Long parseRamAvailable(Server server) {
        try {
            String out = sshService.execute(server,
                    "free -m | grep Mem | awk '{print $7}'");
            return Long.parseLong(out.trim());
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга RAM available: {}", e.getMessage());
            return null;
        }
    }

    private List<DiskInfo> parseDisks(Server server) {
        List<DiskInfo> disks = new ArrayList<>();
        try {
            String out = sshService.execute(server,
                    "df -h --output=source,target,size,avail | tail -n +2");
            for (String line : out.split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;

                String source = parts[0];
                if (source.startsWith("tmpfs") || source.startsWith("devtmpfs")
                        || source.startsWith("udev") || source.startsWith("none")) continue;

                String type = detectDiskType(server, source);

                disks.add(DiskInfo.builder()
                        .name(source)
                        .mount(parts[1])
                        .totalGb(parts[2])
                        .freeGb(parts[3])
                        .type(type)
                        .build());
            }
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга дисков: {}", e.getMessage());
        }
        return disks;
    }

    private String detectDiskType(Server server, String source) {
        try {
            String devName = source.replaceAll("/dev/", "").replaceAll("[0-9]+$", "");
            String out = sshService.execute(server,
                    "cat /sys/block/" + devName + "/queue/rotational 2>/dev/null || echo unknown");
            return switch (out.trim()) {
                case "0" -> "ssd";
                case "1" -> "hdd";
                default -> "unknown";
            };
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String parseOsName(Server server) {
        try {
            return sshService.execute(server,
                    "lsb_release -si 2>/dev/null || cat /etc/os-release | grep '^NAME' | cut -d= -f2 | tr -d '\"'");
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга OS name: {}", e.getMessage());
            return null;
        }
    }

    private String parseOsVersion(Server server) {
        try {
            return sshService.execute(server,
                    "lsb_release -sr 2>/dev/null || cat /etc/os-release | grep '^VERSION_ID' | cut -d= -f2 | tr -d '\"'");
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга OS version: {}", e.getMessage());
            return null;
        }
    }

    private String parseKernel(Server server) {
        try {
            return sshService.execute(server, "uname -r");
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга kernel: {}", e.getMessage());
            return null;
        }
    }

    private String parseGpu(Server server) {
        try {
            String out = sshService.execute(server,
                    "lspci 2>/dev/null | grep -i 'vga\\|3d\\|display' | head -1 | sed 's/.*: //'");
            return out.isBlank() ? null : out;
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга GPU: {}", e.getMessage());
            return null;
        }
    }

    private String executeSafe(Server server, String command) {
        try {
            return sshService.execute(server, command);
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка выполнения команды '{}': {}", command, e.getMessage());
            return "0";
        }
    }

    private BigDecimal parseCpuUsage(Server server) {
        try {
            String cmd =
                    "CPU1=$(awk '/^cpu /{print $2+$3+$4+$5+$6+$7+$8}' /proc/stat); " +
                            "IDLE1=$(awk '/^cpu /{print $5}' /proc/stat); " +
                            "sleep 1; " +
                            "CPU2=$(awk '/^cpu /{print $2+$3+$4+$5+$6+$7+$8}' /proc/stat); " +
                            "IDLE2=$(awk '/^cpu /{print $5}' /proc/stat); " +
                            "TOTAL=$((CPU2-CPU1)); IDLE=$((IDLE2-IDLE1)); " +
                            "if [ \"$TOTAL\" -le 0 ]; then echo 0; else echo \"scale=2; (($TOTAL-$IDLE)*100)/$TOTAL\" | bc; fi";
            return parseBigDecimalSafe(sshService.execute(server, cmd));
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга CPU usage: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal[] parseLoadAverage(Server server) {
        try {
            String raw = sshService.execute(server, "awk '{print $1\"|\"$2\"|\"$3}' /proc/loadavg");
            String[] parts = raw.trim().split("\\|");
            return new BigDecimal[]{
                    parts.length > 0 ? parseBigDecimalSafe(parts[0]) : BigDecimal.ZERO,
                    parts.length > 1 ? parseBigDecimalSafe(parts[1]) : BigDecimal.ZERO,
                    parts.length > 2 ? parseBigDecimalSafe(parts[2]) : BigDecimal.ZERO
            };
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга load average: {}", e.getMessage());
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private BigDecimal[] parseNetworkTotals(Server server) {
        try {
            String command = "awk -F'[: ]+' 'NR>2 { iface=$1; rx=$3; tx=$11; if (iface !~ /^(lo|docker[0-9]*|br-|veth|virbr|tunl|tap|wg)/) { rxsum+=rx; txsum+=tx } } END { printf \"%.2f|%.2f\", rxsum/1024/1024, txsum/1024/1024 }' /proc/net/dev";
            String raw = sshService.execute(server, command);
            String[] parts = raw.trim().split("\\|");
            return new BigDecimal[]{
                    parts.length > 0 ? parseBigDecimalSafe(parts[0]) : BigDecimal.ZERO,
                    parts.length > 1 ? parseBigDecimalSafe(parts[1]) : BigDecimal.ZERO
            };
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга network totals: {}", e.getMessage());
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private int[] parseUsedTotal(Server server, String command) {
        try {
            String raw = sshService.execute(server, command);
            String[] parts = raw.trim().split("/");
            if (parts.length != 2) return new int[]{0, 0};
            return new int[]{parseIntSafe(parts[0]), parseIntSafe(parts[1])};
        } catch (Exception e) {
            log.warn("[HARDWARE] Ошибка парсинга used/total: {}", e.getMessage());
            return new int[]{0, 0};
        }
    }

    private BigDecimal percent(Number used, Number total) {
        double totalValue = total == null ? 0d : total.doubleValue();
        double usedValue = used == null ? 0d : used.doubleValue();
        if (totalValue <= 0d) return BigDecimal.ZERO;
        return BigDecimal.valueOf((usedValue * 100d) / totalValue).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseBigDecimalSafe(String raw) {
        try {
            return BigDecimal.valueOf(Double.parseDouble(raw.trim())).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private int parseIntSafe(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
