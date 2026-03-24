package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BackupRepository extends JpaRepository<Backup, UUID> {

    List<Backup> findAllByMinecraftServerIdOrderByCreatedAtDesc(UUID minecraftServerId);

    int countByMinecraftServerId(UUID minecraftServerId);

    @Query("SELECT COALESCE(SUM(b.sizeMb), 0) FROM Backup b WHERE b.minecraftServer.id = :serverId")
    Integer sumSizeMbByServerId(@Param("serverId") UUID serverId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Backup b WHERE b.minecraftServer.id = :serverId AND b.createdAt < :cutoff")
    int deleteOlderThan(@Param("serverId") UUID serverId, @Param("cutoff") LocalDateTime cutoff);

    long countByMinecraftServerIdIn(List<UUID> mcIds);

    @Query("SELECT COALESCE(SUM(b.sizeMb), 0) FROM Backup b WHERE b.minecraftServer.id IN :mcIds")
    long sumSizeMbByMinecraftServerIdIn(@Param("mcIds") List<UUID> mcIds);
}