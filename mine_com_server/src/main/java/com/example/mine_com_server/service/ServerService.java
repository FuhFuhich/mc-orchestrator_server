package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.request.ServerRequest;
import com.example.mine_com_server.dto.response.NodeMemberResponse;
import com.example.mine_com_server.dto.response.ServerResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.model.User;
import com.example.mine_com_server.repository.NodeMemberRepository;
import com.example.mine_com_server.repository.ServerRepository;
import com.example.mine_com_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServerService {

    private final ServerRepository serverRepository;
    private final UserRepository userRepository;
    private final NodeMemberRepository nodeMemberRepository;
    private final SshService sshService;
    private final HardwareService hardwareService;

    public List<ServerResponse> getAllByUser(UUID userId) {
        return nodeMemberRepository.findAllByUserId(userId).stream()
                .map(nodeMember -> toResponse(nodeMember.getNode(), userId))
                .toList();
    }

    public ServerResponse getById(UUID serverId, UUID requestingUserId) {
        return toResponse(findOrThrow(serverId), requestingUserId);
    }

    public boolean exists(UUID serverId) {
        return serverRepository.existsById(serverId);
    }

    @Transactional
    public ServerResponse create(ServerRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        Server server = Server.builder()
                .name(request.getName())
                .ipAddress(request.getIpAddress())
                .sshPort(request.getSshPort() != null ? request.getSshPort() : 22)
                .sshUser(request.getSshUser())
                .authType(request.getAuthType())
                .sshPrivateKey(request.getSshPrivateKey())
                .sshPassword(request.getSshPassword())
                .description(request.getDescription())
                .os(request.getOs())
                .isActive(true)
                .build();

        server = serverRepository.save(server);

        nodeMemberRepository.save(
                NodeMember.builder()
                        .node(server)
                        .user(user)
                        .role(NodeRole.OWNER)
                        .build()
        );

        final UUID serverId = server.getId();
        CompletableFuture.runAsync(() -> {
            try {
                hardwareService.scanAndSave(serverId);
            } catch (Exception e) {
                log.warn("[HARDWARE] Не удалось просканировать ноду {}: {}", serverId, e.getMessage());
            }
        });

        return toResponse(server, userId);
    }

    @Transactional
    public ServerResponse update(UUID serverId, ServerRequest request) {
        Server server = findOrThrow(serverId);

        server.setName(request.getName());
        server.setIpAddress(request.getIpAddress());
        server.setSshPort(request.getSshPort() != null ? request.getSshPort() : server.getSshPort());
        server.setSshUser(request.getSshUser());
        server.setAuthType(request.getAuthType());
        server.setSshPrivateKey(request.getSshPrivateKey());
        server.setSshPassword(request.getSshPassword());
        server.setDescription(request.getDescription());
        server.setOs(request.getOs());

        return toResponse(serverRepository.save(server), null);
    }

    @Transactional
    public void delete(UUID id) {
        nodeMemberRepository.deleteAllByNodeId(id);
        serverRepository.delete(findOrThrow(id));
        sshService.closeSession(id);
    }

    public java.util.Map<String, Object> checkReachabilitySync(UUID id) {
        return sshService.pingTcp(findOrThrow(id));
    }

    public List<NodeMemberResponse> getMembers(UUID nodeId) {
        return nodeMemberRepository.findAllByNodeIdWithUser(nodeId).stream()
                .map(nodeMember -> {
                    NodeMemberResponse response = new NodeMemberResponse();
                    response.setUserId(nodeMember.getUser().getId());
                    response.setUsername(nodeMember.getUser().getUsername());
                    response.setEmail(nodeMember.getUser().getEmail());
                    response.setRole(nodeMember.getRole().name());
                    response.setCreatedAt(nodeMember.getCreatedAt());
                    return response;
                })
                .toList();
    }

    @Transactional
    public void addMember(UUID nodeId, UUID targetUserId, NodeRole role) {
        if (nodeMemberRepository.existsByNodeIdAndUserId(nodeId, targetUserId)) {
            throw new IllegalStateException("Пользователь уже является участником ноды");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        Server node = findOrThrow(nodeId);

        nodeMemberRepository.save(
                NodeMember.builder()
                        .node(node)
                        .user(user)
                        .role(role)
                        .build()
        );
    }

    @Transactional
    public void updateMemberRole(UUID nodeId, UUID targetUserId, NodeRole role) {
        NodeMember member = nodeMemberRepository.findByNodeIdAndUserId(nodeId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Участник ноды не найден"));

        member.setRole(role);
        nodeMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID nodeId, UUID targetUserId) {
        nodeMemberRepository.deleteByNodeIdAndUserId(nodeId, targetUserId);
    }

    private Server findOrThrow(UUID serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Нода не найдена: " + serverId));
    }

    private ServerResponse toResponse(Server server, UUID requestingUserId) {
        ServerResponse response = new ServerResponse();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setIpAddress(server.getIpAddress());
        response.setSshPort(server.getSshPort());
        response.setSshUser(server.getSshUser());
        response.setAuthType(server.getAuthType());
        response.setDescription(server.getDescription());
        response.setOs(server.getOs());
        response.setIsActive(server.getIsActive());
        response.setCreatedAt(server.getCreatedAt());
        response.setUpdatedAt(server.getUpdatedAt());

        if (requestingUserId != null) {
            nodeMemberRepository.findByNodeIdAndUserId(server.getId(), requestingUserId)
                    .ifPresent(nodeMember -> response.setMyRole(nodeMember.getRole().name()));
        }

        return response;
    }
}