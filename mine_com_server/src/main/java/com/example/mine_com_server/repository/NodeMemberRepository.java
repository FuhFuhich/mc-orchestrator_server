package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeMemberRepository extends JpaRepository<NodeMember, UUID> {

    List<NodeMember> findAllByUserId(UUID userId);

    List<NodeMember> findAllByNodeId(UUID nodeId);

    @Query("SELECT nm FROM NodeMember nm JOIN FETCH nm.user WHERE nm.node.id = :nodeId")
    List<NodeMember> findAllByNodeIdWithUser(@Param("nodeId") UUID nodeId);

    Optional<NodeMember> findByNodeIdAndUserId(UUID nodeId, UUID userId);

    boolean existsByNodeIdAndUserId(UUID nodeId, UUID userId);

    boolean existsByNodeIdAndUserIdAndRole(UUID nodeId, UUID userId, NodeRole role);

    @Modifying
    @Query("DELETE FROM NodeMember nm WHERE nm.node.id = :nodeId")
    void deleteAllByNodeId(@Param("nodeId") UUID nodeId);

    @Modifying
    @Query("DELETE FROM NodeMember nm WHERE nm.node.id = :nodeId AND nm.user.id = :userId")
    void deleteByNodeIdAndUserId(@Param("nodeId") UUID nodeId, @Param("userId") UUID userId);
}