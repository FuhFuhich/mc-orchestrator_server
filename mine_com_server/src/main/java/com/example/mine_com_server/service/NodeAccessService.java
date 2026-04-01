package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.repository.NodeMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
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

    public void requireAnyRole(UUID userId, UUID nodeId, Set<NodeRole> allowedRoles) {
        NodeRole actual = getRole(userId, nodeId);
        if (!allowedRoles.contains(actual)) {
            throw new ForbiddenException("Недостаточно прав для действия: " + actual);
        }
    }

    private int roleLevel(NodeRole role) {
        return switch (role) {
            case OWNER   -> 4;
            case MANAGER -> 3;
            case ADMIN   -> 2;
            case VIEWER  -> 1;
            case USER    -> 0;
        };
    }

    private boolean hasRole(NodeRole actual, NodeRole required) {
        return roleLevel(actual) >= roleLevel(required);
    }

    public boolean canManageMinecraftServers(UUID userId, UUID nodeId) {
        return hasAtLeast(userId, nodeId, NodeRole.ADMIN);
    }

    public boolean canCreateMinecraftServers(UUID userId, UUID nodeId) {
        return hasAtLeast(userId, nodeId, NodeRole.MANAGER);
    }

    public boolean hasAtLeast(UUID userId, UUID nodeId, NodeRole role) {
        try {
            return hasRole(getRole(userId, nodeId), role);
        } catch (ForbiddenException ex) {
            return false;
        }
    }

    public boolean isOwner(UUID userId, UUID nodeId) {
        return nodeMemberRepository.existsByNodeIdAndUserIdAndRole(nodeId, userId, NodeRole.OWNER);
    }
}
