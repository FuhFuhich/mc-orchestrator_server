package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.dto.response.BackupResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.Backup;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.repository.BackupRepository;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackupService {

    private final MinecraftServerRepository mcServerRepository;
    private final BackupRepository backupRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;

    @Async("mc-async-")
    @Transactional
    public CompletableFuture<BackupResponse> createBackup(UUID mcServerId) {
        try {
            MinecraftServer mc = findMcOrThrow(mcServerId);

            String scriptName = mc.isDockerMode() ? "docker-backup.sh" : "backup.sh";
            String rawOutput = sshService.runScript(
                    mc.getNode(),
                    remoteConfig.rootFor(mc.getNode()),
                    remoteConfig.scriptPath(mc.getNode(), scriptName),
                    mc.getId().toString()
            );

            String remotePath = extractArchivePath(rawOutput);
            if (remotePath.isBlank()) {
                throw new IllegalStateException("Скрипт бэкапа не вернул путь к архиву. Raw output: " + rawOutput);
            }

            String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            Integer sizeMb = fetchSizeMb(mc, remotePath);

            Backup backup = Backup.builder()
                    .minecraftServer(mc)
                    .fileName(fileName)
                    .remotePath(remotePath)
                    .sizeMb(sizeMb)
                    .backupType("manual")
                    .build();

            backup = backupRepository.saveAndFlush(backup);
            log.info("[BACKUP] Бэкап создан и сохранён в БД: serverId={}, backupId={}, path={}",
                    mcServerId, backup.getId(), remotePath);

            return CompletableFuture.completedFuture(toResponse(backup));
        } catch (Exception e) {
            log.error("[BACKUP] Не удалось создать/сохранить бэкап для сервера {}: {}",
                    mcServerId, e.getMessage(), e);
            throw e;
        }
    }

    public List<BackupResponse> getAll(UUID mcServerId) {
        return backupRepository
                .findAllByMinecraftServerIdOrderByCreatedAtDesc(mcServerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public UUID getNodeIdByBackupId(UUID backupId) {
        return findBackupOrThrow(backupId).getMinecraftServer().getNode().getId();
    }

    @Transactional(readOnly = true)
    public void restore(UUID backupId) {
        try {
            Backup backup = findBackupOrThrow(backupId);
            MinecraftServer mc = backup.getMinecraftServer();

            log.info("[RESTORE] Начало восстановления: backupId={}, mcId={}, archive={}",
                    backupId, mc.getId(), backup.getRemotePath());

            runRestoreScript(mc, backup.getRemotePath());

            log.info("[RESTORE] Сервер {} восстановлен из бэкапа {}", mc.getId(), backupId);
        } catch (Exception e) {
            log.error("[RESTORE] Ошибка восстановления backupId={}: {}", backupId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void restore(UUID mcServerId, String archivePath) {
        try {
            MinecraftServer mc = findMcOrThrow(mcServerId);

            log.info("[RESTORE] Начало восстановления: mcId={}, archive={}",
                    mcServerId, archivePath);

            runRestoreScript(mc, archivePath);

            log.info("[RESTORE] Сервер {} восстановлен из {}", mcServerId, archivePath);
        } catch (Exception e) {
            log.error("[RESTORE] Ошибка восстановления mcId={}: {}", mcServerId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void delete(UUID backupId) {
        if (!backupRepository.existsById(backupId)) {
            throw new NotFoundException("Бэкап не найден: " + backupId);
        }
        backupRepository.deleteById(backupId);
        log.info("[BACKUP] Запись бэкапа {} удалена из БД", backupId);
    }

    private void runRestoreScript(MinecraftServer mc, String archivePath) {
        String scriptName = mc.isDockerMode() ? "docker-restore.sh" : "restore.sh";
        sshService.runScript(
                mc.getNode(),
                remoteConfig.rootFor(mc.getNode()),
                remoteConfig.scriptPath(mc.getNode(), scriptName),
                mc.getId().toString(),
                archivePath
        );
    }

    private Integer fetchSizeMb(MinecraftServer mc, String remotePath) {
        try {
            String raw = sshService.execute(
                    mc.getNode(),
                    "du -sm " + sshService.quote(remotePath) + " 2>/dev/null | cut -f1 || echo '0'"
            );
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            log.warn("[BACKUP] Не удалось определить размер бэкапа {}: {}", remotePath, e.getMessage());
            return null;
        }
    }

    private String extractArchivePath(String rawOutput) {
        if (rawOutput == null) {
            return "";
        }

        return Arrays.stream(rawOutput.replace("\r", "").split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> line.endsWith(".tar.gz") || line.endsWith(".zip"))
                .reduce((first, second) -> second)
                .orElse("");
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

    private MinecraftServer findMcOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    private Backup findBackupOrThrow(UUID id) {
        return backupRepository.findByIdWithMinecraftServerAndNode(id)
                .orElseThrow(() -> new NotFoundException("Бэкап не найден: " + id));
    }
}
