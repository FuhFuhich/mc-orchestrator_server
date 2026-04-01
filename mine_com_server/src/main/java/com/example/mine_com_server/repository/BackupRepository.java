package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.Backup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackupRepository extends JpaRepository<Backup, UUID> {

    List<Backup> findAllByMinecraftServerIdOrderByCreatedAtDesc(UUID minecraftServerId);

    @Query("""
        select b
        from Backup b
        join fetch b.minecraftServer ms
        join fetch ms.node
        where b.id = :id
    """)
    Optional<Backup> findByIdWithMinecraftServerAndNode(@Param("id") UUID id);

    long countByMinecraftServerIdIn(Collection<UUID> minecraftServerIds);
}
