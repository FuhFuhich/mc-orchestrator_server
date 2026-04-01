package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.NodeHardware;
import com.example.mine_com_server.model.NodeHardware.DiskInfo;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.NodeHardwareRepository;
import com.example.mine_com_server.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                // Пропускаем tmpfs, devtmpfs и прочие виртуальные
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
}