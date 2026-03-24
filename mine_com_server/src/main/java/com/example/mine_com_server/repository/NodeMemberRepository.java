package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeMemberRepository extends JpaRepository<NodeMember, UUID> {

    Optional<NodeMember> findByNodeIdAndUserId(UUID nodeId, UUID userId);

    List<NodeMember> findAllByNodeId(UUID nodeId);

    List<NodeMember> findAllByUserId(UUID userId);

    boolean existsByNodeIdAndUserIdAndRole(UUID nodeId, UUID userId, NodeRole role);

    void deleteByNodeIdAndUserId(UUID nodeId, UUID userId);
}