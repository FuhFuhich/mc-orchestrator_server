package com.example.mine_com_server.service;

import com.example.mine_com_server.model.AuditLog;
import com.example.mine_com_server.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("mc-async-")
    @Transactional
    public void record(UUID userId, String action,
                    String entityType, UUID entityId,
                    String details, String ip) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(ip)
                    .build());
        } catch (Exception e) {
            log.error("[AUDIT] Ошибка записи: {}", e.getMessage());
        }
    }

    @Async("mc-async-")
    public void log(UUID userId, String action, String entityType, UUID entityId) {
        record(userId, action, entityType, entityId, null, null);
    }

    public List<AuditLog> getByUser(UUID userId, int hours) {
        LocalDateTime from = LocalDateTime.now().minusHours(hours);
        return auditLogRepository
                .findAllByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, from);
    }

    public List<AuditLog> getByEntity(UUID entityId) {
        return auditLogRepository.findAllByEntityIdOrderByCreatedAtDesc(entityId);
    }

    @Scheduled(cron = "0 0 3 * * *") // каждый день в 3:00
    @Transactional
    public void cleanOldLogs() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        auditLogRepository.deleteByCreatedAtBefore(threshold);
        log.info("[AUDIT] Очистка логов старше 30 дней выполнена");
    }
}