package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.request.ServerRequest;
import com.example.mine_com_server.dto.response.ServerResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.*;
import com.example.mine_com_server.repository.NodeMemberRepository;
import com.example.mine_com_server.repository.ServerRepository;
import com.example.mine_com_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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

    // ===== ПОЛУЧИТЬ ВСЕ НОДЫ ПОЛЬЗОВАТЕЛЯ =====

    public List<ServerResponse> getAllByUser(UUID userId) {
        return nodeMemberRepository.findAllByUserId(userId).stream()
                .map(nm -> toResponse(nm.getNode()))
                .toList();
    }

    // ===== ПОЛУЧИТЬ НОДУ ПО ID =====

    public ServerResponse getById(UUID serverId) {
        return toResponse(findOrThrow(serverId));
    }

    // ===== СОЗДАТЬ НОДУ =====

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

        // Назначаем создателя OWNER
        nodeMemberRepository.save(NodeMember.builder()
                .node(server)
                .user(user)
                .role(NodeRole.OWNER)
                .build());

        log.info("Нода создана: {} ({}), owner: {}", server.getName(), server.getIpAddress(), userId);

        final UUID serverId = server.getId();
        CompletableFuture.runAsync(() -> {
            try {
                hardwareService.scanAndSave(serverId);
            } catch (Exception e) {
                log.warn("[HARDWARE] Не удалось просканировать ноду {}: {}", serverId, e.getMessage());
            }
        });

        return toResponse(server);
    }

    // ===== ОБНОВИТЬ НОДУ =====

    @Transactional
    public ServerResponse update(UUID serverId, ServerRequest request) {
        Server server = findOrThrow(serverId);

        server.setName(request.getName());
        server.setIpAddress(request.getIpAddress());
        if (request.getSshPort() != null) server.setSshPort(request.getSshPort());
        server.setSshUser(request.getSshUser());
        server.setAuthType(request.getAuthType());
        if (request.getSshPrivateKey() != null) server.setSshPrivateKey(request.getSshPrivateKey());
        if (request.getSshPassword() != null) server.setSshPassword(request.getSshPassword());
        server.setDescription(request.getDescription());
        server.setOs(request.getOs());

        return toResponse(serverRepository.save(server));
    }

    // ===== УДАЛИТЬ НОДУ (мягкое удаление) =====

    @Transactional
    public void delete(UUID serverId) {
        Server server = findOrThrow(serverId);
        server.setIsActive(false);
        serverRepository.save(server);

        sshService.closeSession(serverId);

        log.info("Нода деактивирована: {}", serverId);
    }

    // ===== ПРОВЕРИТЬ ДОСТУПНОСТЬ НОДЫ =====

    @Async("mc-async-")
    public CompletableFuture<Boolean> checkReachability(UUID serverId) {
        Server server = findOrThrow(serverId);
        boolean reachable = sshService.isReachable(server);
        log.info("Проверка ноды {}: {}", server.getIpAddress(), reachable ? "доступна" : "недоступна");
        return CompletableFuture.completedFuture(reachable);
    }

    // ===== ДОБАВИТЬ УЧАСТНИКА К НОДЕ =====

    @Transactional
    public void addMember(UUID serverId, UUID targetUserId, NodeRole role) {
        if (nodeMemberRepository.findByNodeIdAndUserId(serverId, targetUserId).isPresent()) {
            throw new IllegalStateException("Пользователь уже имеет доступ к этой ноде");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        Server server = findOrThrow(serverId);

        nodeMemberRepository.save(NodeMember.builder()
                .node(server)
                .user(user)
                .role(role)
                .build());

        log.info("Участник {} добавлен к ноде {} с ролью {}", targetUserId, serverId, role);
    }

    // ===== УБРАТЬ УЧАСТНИКА С НОДЫ =====

    @Transactional
    public void removeMember(UUID serverId, UUID targetUserId) {
        if (!nodeMemberRepository.findByNodeIdAndUserId(serverId, targetUserId).isPresent()) {
            throw new NotFoundException("Участник не найден");
        }
        nodeMemberRepository.deleteByNodeIdAndUserId(serverId, targetUserId);
        log.info("Участник {} удалён с ноды {}", targetUserId, serverId);
    }

    // ===== ИЗМЕНИТЬ РОЛЬ УЧАСТНИКА =====

    @Transactional
    public void updateMemberRole(UUID serverId, UUID targetUserId, NodeRole newRole) {
        NodeMember member = nodeMemberRepository.findByNodeIdAndUserId(serverId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Участник не найден"));

        if (member.getRole() == NodeRole.OWNER) {
            throw new IllegalStateException("Нельзя изменить роль OWNER");
        }

        member.setRole(newRole);
        nodeMemberRepository.save(member);

        log.info("Роль участника {} на ноде {} изменена на {}", targetUserId, serverId, newRole);
    }

    // ===== СПИСОК УЧАСТНИКОВ НОДЫ =====

    public List<NodeMember> getMembers(UUID serverId) {
        return nodeMemberRepository.findAllByNodeId(serverId);
    }

    // ===== УТИЛИТЫ =====

    public Server findOrThrow(UUID serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Нода не найдена: " + serverId));
    }

    public ServerResponse toResponse(Server s) {
        ServerResponse r = new ServerResponse();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setIpAddress(s.getIpAddress());
        r.setSshPort(s.getSshPort());
        r.setSshUser(s.getSshUser());
        r.setAuthType(s.getAuthType());
        r.setDescription(s.getDescription());
        r.setOs(s.getOs());
        r.setIsActive(s.getIsActive());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }
}