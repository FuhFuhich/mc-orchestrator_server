package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.Metrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricsRepository extends JpaRepository<Metrics, UUID> {

    List<Metrics> findAllByMinecraftServerIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            UUID minecraftServerId,
            LocalDateTime from,
            LocalDateTime to
    );

    Optional<Metrics> findTopByMinecraftServerIdOrderByRecordedAtDesc(UUID minecraftServerId);

    @Query("""
        SELECT m FROM Metrics m
        WHERE m.minecraftServer.id = :mcServerId
          AND m.recordedAt >= :from
        ORDER BY m.recordedAt ASC
    """)
    List<Metrics> findAllByMcServerIdAfter(
            @Param("mcServerId") UUID mcServerId,
            @Param("from") LocalDateTime from
    );

    @Query("""
        SELECT m FROM Metrics m
        WHERE m.minecraftServer.node.id = :nodeId
          AND m.recordedAt >= :from
        ORDER BY m.recordedAt ASC
    """)
    List<Metrics> findAllByNodeIdAfter(
            @Param("nodeId") UUID nodeId,
            @Param("from") LocalDateTime from
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM Metrics m WHERE m.recordedAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
        SELECT COALESCE(SUM(m.crashesLast24h), 0)
        FROM Metrics m
        WHERE m.minecraftServer.id = :serverId
          AND m.recordedAt >= :from
    """)
    Integer sumCrashesLast24h(
            @Param("serverId") UUID serverId,
            @Param("from") LocalDateTime from
    );

    Page<Metrics> findAllByMinecraftServerIdAndRecordedAtAfter(
            UUID mcServerId, LocalDateTime from, Pageable pageable);

    Page<Metrics> findAllByMinecraftServer_Node_IdAndRecordedAtAfter(
            UUID nodeId, LocalDateTime from, Pageable pageable);
}