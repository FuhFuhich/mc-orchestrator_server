package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.request.ServerRequest;
import com.example.mine_com_server.dto.response.NodeMemberResponse;
import com.example.mine_com_server.dto.response.ServerResponse;
import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.NodeAccessService;
import com.example.mine_com_server.service.ServerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<List<ServerResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(serverService.getAllByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServerResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.VIEWER);
        return ResponseEntity.ok(serverService.getById(id, userId));
    }

    @PostMapping
    public ResponseEntity<ServerResponse> create(
            @RequestBody @Valid ServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ServerResponse response = serverService.create(request, userId);
        auditService.record(userId, AuditActions.CREATE_NODE, "NODE", response.getId(), null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServerResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid ServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.OWNER);
        return ResponseEntity.ok(serverService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.OWNER);
        auditService.record(userId, AuditActions.DELETE_NODE, "NODE", id, null, httpRequest.getRemoteAddr());
        serverService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/check")
    public ResponseEntity<Map<String, Object>> checkReachability(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.VIEWER);
        return ResponseEntity.ok(serverService.checkReachabilitySync(id));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<NodeMemberResponse>> getMembers(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.ADMIN);
        return ResponseEntity.ok(serverService.getMembers(id));
    }

    @PostMapping("/{id}/members/{targetUserId}")
    public ResponseEntity<Void> addMember(
            @PathVariable UUID id,
            @PathVariable UUID targetUserId,
            @RequestParam(defaultValue = "VIEWER") NodeRole role,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.OWNER);
        serverService.addMember(id, targetUserId, role);
        auditService.record(userId, AuditActions.ADD_MEMBER, "NODE", id,
                "targetUserId=" + targetUserId + " role=" + role, httpRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/members/{targetUserId}")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable UUID id,
            @PathVariable UUID targetUserId,
            @RequestParam NodeRole role,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.OWNER);
        serverService.updateMemberRole(id, targetUserId, role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, id, NodeRole.OWNER);
        serverService.removeMember(id, targetUserId);
        auditService.record(userId, AuditActions.REMOVE_MEMBER, "NODE", id,
                "targetUserId=" + targetUserId, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}