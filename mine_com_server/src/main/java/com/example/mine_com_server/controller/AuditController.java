package com.example.mine_com_server.controller;

import com.example.mine_com_server.model.AuditLog;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
import com.example.mine_com_server.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final ServerService serverService;
    private final MinecraftServerService minecraftServerService;
    private final NodeAccessService nodeAccessService;

    @GetMapping("/me")
    public ResponseEntity<List<AuditLog>> getMyLogs(@RequestParam(defaultValue = "24") int hours,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(auditService.getByUser(userId, hours));
    }

    @GetMapping("/entity/{entityId}")
    public ResponseEntity<List<AuditLog>> getByEntity(@PathVariable UUID entityId,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (serverService.exists(entityId)) {
            nodeAccessService.requireRole(userId, entityId, NodeRole.VIEWER);
        } else {
            UUID nodeId = minecraftServerService.getNodeId(entityId);
            nodeAccessService.requireRole(userId, nodeId, NodeRole.VIEWER);
        }
        return ResponseEntity.ok(auditService.getByEntity(entityId));
    }
}
