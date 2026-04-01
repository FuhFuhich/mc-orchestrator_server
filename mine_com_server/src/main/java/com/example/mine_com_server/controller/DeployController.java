package com.example.mine_com_server.controller;

import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.DeployService;
import com.example.mine_com_server.service.DockerDeployService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/deploy")
@RequiredArgsConstructor
public class DeployController {

    private final DeployService deployService;
    private final DockerDeployService dockerDeployService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;

    @PostMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> deploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);

        auditService.record(userId, AuditActions.DEPLOY_SERVER, "MC_SERVER", id, null, httpRequest.getRemoteAddr());

        MinecraftServer mc = mcServerService.findOrThrow(id);
        if (mc.isDockerMode()) {
            return dockerDeployService.deploy(id)
                    .thenApply(v -> ResponseEntity.ok(Map.of("status", "deploying", "mode", "docker")));
        }
        return deployService.deploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "deploying", "mode", "screen")));
    }

    @PostMapping("/{id}/redeploy")
    public CompletableFuture<ResponseEntity<Map<String, String>>> redeploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);

        auditService.record(userId, AuditActions.REDEPLOY_SERVER, "MC_SERVER", id, null, httpRequest.getRemoteAddr());

        MinecraftServer mc = mcServerService.findOrThrow(id);
        if (mc.isDockerMode()) {
            return dockerDeployService.redeploy(id)
                    .thenApply(v -> ResponseEntity.ok(Map.of("status", "redeploying", "mode", "docker")));
        }
        return deployService.redeploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "redeploying", "mode", "screen")));
    }

    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> undeploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);

        auditService.record(userId, AuditActions.UNDEPLOY_SERVER, "MC_SERVER", id, null, httpRequest.getRemoteAddr());

        MinecraftServer mc = mcServerService.findOrThrow(id);
        if (mc.isDockerMode()) {
            return dockerDeployService.undeploy(id)
                    .thenApply(v -> ResponseEntity.ok(Map.of("status", "undeployed", "mode", "docker")));
        }
        return deployService.undeploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "undeployed", "mode", "screen")));
    }
}
