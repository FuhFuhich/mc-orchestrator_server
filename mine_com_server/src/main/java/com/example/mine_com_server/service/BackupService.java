package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.response.BackupResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.Backup;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.BackupRepository;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackupService {

    private static final String SERVERS_DIR = "/home/sha/mc-com/servers";
    private static final String BACKUPS_DIR  = "/home/sha/mc-com/backups";
    private static final String BACKUP_SCRIPT  = "/home/sha/mc-com/scripts/backup.sh";
    private static final String CLEANUP_SCRIPT = "/home/sha/mc-com/scripts/cleanup_backups.sh";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BackupRepository backupRepository;
    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;

    // ===== СОЗДАТЬ БЭКАП ВРУЧНУЮ =====

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<BackupResponse> createBackup(UUID mcServerId) {
        return CompletableFuture.completedFuture(
                doBackup(findOrThrow(mcServerId), "manual")
        );
    }

    // ===== АВТО-БЭКАП ПО РАСПИСАНИЮ (каждые 10 мин проверяем) =====

    @Scheduled(fixedDelay = 240 * 60 * 1000)
    @Transactional
    public void scheduledBackup() {
        List<MinecraftServer> servers = mcServerRepository.findAllByBackupEnabledTrue();
        for (MinecraftServer mc : servers) {
            try {
                if (isBackupDue(mc)) {
                    log.info("[BACKUP] Плановый бэкап: {}", mc.getName());
                    doBackup(mc, "scheduled");
                }
            } catch (Exception e) {
                log.error("[BACKUP] Ошибка авто-бэкапа {}: {}", mc.getName(), e.getMessage());
            }
        }
    }

    // ===== ВОССТАНОВИТЬ ИЗ БЭКАПА =====

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<Void> restore(UUID backupId) {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new NotFoundException("Бэкап не найден: " + backupId));

        MinecraftServer mc = backup.getMinecraftServer();
        Server node = mc.getNode();

        try {
            log.info("[BACKUP] Восстановление {} из {}", mc.getName(), backup.getFileName());

            if ("online".equals(mc.getStatus())) {
                sshService.execute(node, buildStopCommand(mc));
                Thread.sleep(5000);
            }

            String serverDir = SERVERS_DIR + "/" + mc.getId();

            sshService.execute(node, "rm -rf " + serverDir + "/world "
                    + serverDir + "/world_nether "
                    + serverDir + "/world_the_end");

            sshService.execute(node, String.format(
                    "tar -xzf %s -C %s",
                    backup.getRemotePath(), serverDir
            ));

            sshService.execute(node, buildStartCommand(mc));
            mc.setStatus("online");
            mcServerRepository.save(mc);

            log.info("[BACKUP] Восстановление {} завершено", mc.getName());

        } catch (Exception e) {
            mc.setStatus("offline");
            mcServerRepository.save(mc);
            log.error("[BACKUP] Ошибка восстановления: {}", e.getMessage());
            throw new RuntimeException("Ошибка восстановления: " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== УДАЛИТЬ БЭКАП =====

    @Transactional
    public void delete(UUID backupId) {
        Backup backup = backupRepository.findById(backupId)
                .orElseThrow(() -> new NotFoundException("Бэкап не найден: " + backupId));

        Server node = backup.getMinecraftServer().getNode();
        sshService.execute(node, "rm -f " + backup.getRemotePath());
        backupRepository.delete(backup);
        log.info("[BACKUP] Бэкап удалён: {}", backup.getFileName());
    }

    // ===== СПИСОК БЭКАПОВ =====

    public List<BackupResponse> getAll(UUID mcServerId) {
        return backupRepository
                .findAllByMinecraftServerIdOrderByCreatedAtDesc(mcServerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ===== ОЧИСТКА СТАРЫХ БЭКАПОВ (каждый час) =====

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    @Transactional
    public void cleanOldBackups() {
        List<MinecraftServer> servers = mcServerRepository.findAllByBackupAutoDeleteTrue();
        for (MinecraftServer mc : servers) {
            try {
                if (mc.getBackupDeleteAfterHours() == null) continue;

                sshService.execute(mc.getNode(), String.format(
                        "%s %s %d",
                        CLEANUP_SCRIPT, mc.getId(), mc.getBackupDeleteAfterHours()
                ));

                LocalDateTime cutoff = LocalDateTime.now()
                        .minusHours(mc.getBackupDeleteAfterHours());
                int deleted = backupRepository.deleteOlderThan(mc.getId(), cutoff);
                if (deleted > 0) {
                    log.info("[BACKUP] Удалено старых бэкапов для {}: {}", mc.getName(), deleted);
                }
            } catch (Exception e) {
                log.warn("[BACKUP] Ошибка очистки для {}: {}", mc.getName(), e.getMessage());
            }
        }
    }

    // ===== ВНУТРЕННИЙ МЕТОД БЭКАПА =====

    private BackupResponse doBackup(MinecraftServer mc, String type) {
        Server node = mc.getNode();
        String timestamp = LocalDateTime.now().format(FMT);
        String backupDir  = BACKUPS_DIR + "/" + mc.getId();
        String backupFile = backupDir + "/" + timestamp + ".tar.gz";

        sshService.execute(node, "mkdir -p " + backupDir);

        String output = sshService.execute(node,
                String.format("%s %s %s", BACKUP_SCRIPT, mc.getId(), backupDir)
        );
        log.info("[BACKUP] Скрипт {}: {}", mc.getName(), output.trim());

        String sizeRaw = sshService.execute(node,
                "du -sm " + backupFile + " 2>/dev/null | cut -f1 || echo '0'"
        );

        Backup backup = Backup.builder()
                .minecraftServer(mc)
                .fileName(timestamp + ".tar.gz")
                .remotePath(backupFile)
                .sizeMb(parseInt(sizeRaw))
                .backupType(type)
                .createdAt(LocalDateTime.now())
                .build();

        backupRepository.save(backup);
        log.info("[BACKUP] Бэкап создан: {} ({} МБ)", backup.getFileName(), backup.getSizeMb());
        return toResponse(backup);
    }

    // ===== ПРОВЕРКА — НУЖЕН ЛИ БЭКАП =====

    private boolean isBackupDue(MinecraftServer mc) {
        if (mc.getBackupIntervalHours() == null) return false;

        return backupRepository
                .findAllByMinecraftServerIdOrderByCreatedAtDesc(mc.getId())
                .stream()
                .findFirst()
                .map(last -> last.getCreatedAt()
                        .plusHours(mc.getBackupIntervalHours())
                        .isBefore(LocalDateTime.now()))
                .orElse(true);
    }

    // ===== NODEИД ПО BACKUP ID (для проверки прав) =====

    public UUID getNodeIdByBackupId(UUID backupId) {
        return backupRepository.findById(backupId)
                .map(b -> b.getMinecraftServer().getNode().getId())
                .orElseThrow(() -> new NotFoundException("Бэкап не найден: " + backupId));
    }

    // ===== КОМАНДЫ START / STOP =====

    private String buildStartCommand(MinecraftServer mc) {
        String serverDir = SERVERS_DIR + "/" + mc.getId();
        if ("docker".equals(mc.getDeployTarget())) {
            return "docker start mc-" + mc.getId();
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

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    private int parseInt(String raw) {
        try { return Integer.parseInt(raw.trim()); }
        catch (Exception e) { return 0; }
    }

    private BackupResponse toResponse(Backup b) {
        BackupResponse r = new BackupResponse();
        r.setId(b.getId());
        r.setMinecraftServerId(b.getMinecraftServer().getId());
        r.setMinecraftServerName(b.getMinecraftServer().getName());
        r.setFileName(b.getFileName());
        r.setRemotePath(b.getRemotePath());
        r.setSizeMb(b.getSizeMb());
        r.setBackupType(b.getBackupType());
        r.setCreatedAt(b.getCreatedAt());
        return r;
    }
}