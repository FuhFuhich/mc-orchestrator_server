package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.repository.NodeMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeAccessServiceTest {

    @Mock
    private NodeMemberRepository nodeMemberRepository;

    private NodeAccessService nodeAccessService;

    private UUID userId;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        nodeAccessService = new NodeAccessService(nodeMemberRepository);
        userId = UUID.randomUUID();
        nodeId = UUID.randomUUID();
    }

    @Test
    void getRole_returnsRole_whenMembershipExists() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.MANAGER)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        NodeRole role = nodeAccessService.getRole(userId, nodeId);

        assertEquals(NodeRole.MANAGER, role);
    }

    @Test
    void getRole_throwsForbidden_whenMembershipMissing() {
        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.empty());

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> nodeAccessService.getRole(userId, nodeId));

        assertEquals("Нет доступа к ноде", ex.getMessage());
    }

    @Test
    void requireRole_passes_whenActualRoleIsHigherThanRequired() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.OWNER)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        assertDoesNotThrow(() -> nodeAccessService.requireRole(userId, nodeId, NodeRole.ADMIN));
    }

    @Test
    void requireRole_throwsForbidden_whenRoleIsInsufficient() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.VIEWER)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> nodeAccessService.requireRole(userId, nodeId, NodeRole.ADMIN));

        assertTrue(ex.getMessage().contains("Требуется роль: ADMIN"));
    }

    @Test
    void requireAnyRole_passes_whenActualRoleIncluded() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.ADMIN)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        assertDoesNotThrow(() -> nodeAccessService.requireAnyRole(
                userId,
                nodeId,
                Set.of(NodeRole.ADMIN, NodeRole.MANAGER)
        ));
    }

    @Test
    void hasAtLeast_returnsFalse_whenUserHasNoAccess() {
        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.empty());

        assertFalse(nodeAccessService.hasAtLeast(userId, nodeId, NodeRole.VIEWER));
    }

    @Test
    void canManageMinecraftServers_returnsTrue_forAdmin() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.ADMIN)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        assertTrue(nodeAccessService.canManageMinecraftServers(userId, nodeId));
    }

    @Test
    void canCreateMinecraftServers_returnsFalse_forAdmin_becauseManagerRequired() {
        NodeMember member = NodeMember.builder()
                .role(NodeRole.ADMIN)
                .build();

        when(nodeMemberRepository.findByNodeIdAndUserId(nodeId, userId))
                .thenReturn(Optional.of(member));

        assertFalse(nodeAccessService.canCreateMinecraftServers(userId, nodeId));
    }

    @Test
    void isOwner_delegatesToRepository() {
        when(nodeMemberRepository.existsByNodeIdAndUserIdAndRole(nodeId, userId, NodeRole.OWNER))
                .thenReturn(true);

        assertTrue(nodeAccessService.isOwner(userId, nodeId));
        verify(nodeMemberRepository).existsByNodeIdAndUserIdAndRole(nodeId, userId, NodeRole.OWNER);
    }
}
