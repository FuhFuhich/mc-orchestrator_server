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

    @Query("""
        SELECT ms FROM MinecraftServer ms
        WHERE ms.node.id IN (
            SELECT nm.node.id FROM NodeMember nm WHERE nm.user.id = :userId
        )
    """)
    List<MinecraftServer> findAllByUserId(@Param("userId") UUID userId);

    long countByNodeIdAndStatus(UUID nodeId, String status);
    boolean existsByNameAndNodeId(String name, UUID nodeId);
    boolean existsByNodeIdAndGamePort(UUID nodeId, Integer gamePort);

    List<MinecraftServer> findAllByBackupEnabledTrue();
    List<MinecraftServer> findAllByBackupAutoDeleteTrue();
    List<MinecraftServer> findAllByNodeIdIn(List<UUID> nodeIds);
    int countByNodeIdInAndStatus(List<UUID> nodeIds, String status);
}
