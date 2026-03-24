package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.MinecraftServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MinecraftServerRepository extends JpaRepository<MinecraftServer, UUID> {

    List<MinecraftServer> findAllByNodeId(UUID nodeId);

    List<MinecraftServer> findAllByStatus(String status);

    // Все серверы пользователя через ноды
    @Query("""
        SELECT ms FROM MinecraftServer ms
        WHERE ms.node.id IN (
            SELECT us.server.id FROM UserServer us WHERE us.user.id = :userId
        )
    """)
    List<MinecraftServer> findAllByUserId(@Param("userId") UUID userId);

    // Количество онлайн-серверов на ноде
    long countByNodeIdAndStatus(UUID nodeId, String status);

    boolean existsByNameAndNodeId(String name, UUID nodeId);

    // Для авто-бэкапа
    List<MinecraftServer> findAllByBackupEnabledTrue();

    // Для авто-очистки бэкапов
    List<MinecraftServer> findAllByBackupAutoDeleteTrue();

    List<MinecraftServer> findAllByNodeIdIn(List<UUID> nodeIds);
    int countByNodeIdInAndStatus(List<UUID> nodeIds, String status);
}