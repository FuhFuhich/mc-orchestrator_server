package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.request.MinecraftServerRequest;
import com.example.mine_com_server.dto.response.MinecraftServerResponse;
import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
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
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/mc-servers")
@RequiredArgsConstructor
public class MinecraftServerController {

    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<List<MinecraftServerResponse>> getAll(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(mcServerService.getAllByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinecraftServerResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(mcServerService.getById(id));
    }

    @GetMapping("/node/{nodeId}")
    public ResponseEntity<List<MinecraftServerResponse>> getByNode(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, nodeId, NodeRole.VIEWER);
        return ResponseEntity.ok(mcServerService.getAllByNode(nodeId));
    }

    @PostMapping
    public ResponseEntity<MinecraftServerResponse> create(
            @RequestBody @Valid MinecraftServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, request.getNodeId(), NodeRole.OWNER);
        MinecraftServerResponse response = mcServerService.create(request);
        auditService.record(userId, AuditActions.CREATE_SERVER, "MC_SERVER",
                response.getId(), null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinecraftServerResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid MinecraftServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);
        return ResponseEntity.ok(mcServerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);
        auditService.record(userId, AuditActions.DELETE_SERVER, "MC_SERVER",
                id, null, httpRequest.getRemoteAddr());
        mcServerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    public CompletableFuture<ResponseEntity<Map<String, String>>> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        auditService.record(userId, AuditActions.START_SERVER, "MC_SERVER",
                id, null, httpRequest.getRemoteAddr());
        return mcServerService.startAsync(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "starting")));
    }

    @PostMapping("/{id}/stop")
    public CompletableFuture<ResponseEntity<Map<String, String>>> stop(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        auditService.record(userId, AuditActions.STOP_SERVER, "MC_SERVER",
                id, null, httpRequest.getRemoteAddr());
        return mcServerService.stopAsync(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "stopping")));
    }

    @PostMapping("/{id}/restart")
    public CompletableFuture<ResponseEntity<Map<String, String>>> restart(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        auditService.record(userId, AuditActions.RESTART_SERVER, "MC_SERVER",
                id, null, httpRequest.getRemoteAddr());
        return mcServerService.restartAsync(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "restarting")));
    }

    @GetMapping("/{id}/console")
    public ResponseEntity<List<String>> getConsoleLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int lines,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(mcServerService.getConsoleLogs(id, lines));
    }

    @PostMapping("/{id}/console")
    public ResponseEntity<Map<String, String>> sendCommand(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        String command = body.get("command");
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Команда не может быть пустой"));
        }
        auditService.record(userId, AuditActions.SEND_COMMAND, "MC_SERVER",
                id, command, httpRequest.getRemoteAddr());
        mcServerService.sendCommand(id, command);
        return ResponseEntity.ok(Map.of("status", "sent", "command", command));
    }
}