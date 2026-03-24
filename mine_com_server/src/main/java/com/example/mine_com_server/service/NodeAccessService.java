package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.repository.NodeMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NodeAccessService {

    private final NodeMemberRepository nodeMemberRepository;

    public NodeRole getRole(UUID userId, UUID nodeId) {
        return nodeMemberRepository
                .findByNodeIdAndUserId(nodeId, userId)
                .map(NodeMember::getRole)
                .orElseThrow(() -> new ForbiddenException("Нет доступа к ноде"));
    }

    public void requireRole(UUID userId, UUID nodeId, NodeRole required) {
        NodeRole actual = getRole(userId, nodeId);
        if (!hasRole(actual, required)) {
            throw new ForbiddenException("Требуется роль: " + required + ", у вас: " + actual);
        }
    }

    private boolean hasRole(NodeRole actual, NodeRole required) {
        return actual.ordinal() <= required.ordinal();
    }

    public boolean isOwner(UUID userId, UUID nodeId) {
        return nodeMemberRepository
                .existsByNodeIdAndUserIdAndRole(nodeId, userId, NodeRole.OWNER);
    }
}