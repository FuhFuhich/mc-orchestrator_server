package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuditLog> findAllByEntityIdOrderByCreatedAtDesc(UUID entityId);

    List<AuditLog> findAllByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, LocalDateTime after);

    void deleteByCreatedAtBefore(LocalDateTime threshold);
}