package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.response.BackupResponse;
import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.BackupService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;

    @GetMapping("/{mcServerId}")
    public ResponseEntity<List<BackupResponse>> getAll(
            @PathVariable UUID mcServerId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(mcServerId), NodeRole.VIEWER);
        return ResponseEntity.ok(backupService.getAll(mcServerId));
    }

    @PostMapping("/{mcServerId}")
    public ResponseEntity<Map<String, String>> create(
            @PathVariable UUID mcServerId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(mcServerId), NodeRole.ADMIN);

        auditService.record(
                userId,
                AuditActions.CREATE_BACKUP,
                "MC_SERVER",
                mcServerId,
                null,
                httpRequest.getRemoteAddr()
        );

        backupService.createBackup(mcServerId); // async
        return ResponseEntity.ok(Map.of("status", "backup_started"));
    }

    @PostMapping("/{backupId}/restore")
    public ResponseEntity<Map<String, String>> restore(
            @PathVariable UUID backupId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID nodeId = backupService.getNodeIdByBackupId(backupId);
        nodeAccessService.requireRole(userId, nodeId, NodeRole.OWNER);

        auditService.record(
                userId,
                AuditActions.RESTORE_BACKUP,
                "BACKUP",
                backupId,
                null,
                httpRequest.getRemoteAddr()
        );

        backupService.restore(backupId); // sync
        return ResponseEntity.ok(Map.of("status", "restored"));
    }

    @DeleteMapping("/{backupId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID backupId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID nodeId = backupService.getNodeIdByBackupId(backupId);
        nodeAccessService.requireRole(userId, nodeId, NodeRole.ADMIN);

        auditService.record(
                userId,
                AuditActions.DELETE_BACKUP,
                "BACKUP",
                backupId,
                null,
                httpRequest.getRemoteAddr()
        );

        backupService.delete(backupId);
        return ResponseEntity.noContent().build();
    }
}