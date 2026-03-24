package com.example.mine_com_server.controller;

import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.DeployService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
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
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;

    // POST /api/deploy/{id} — задеплоить (OWNER)
    @PostMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> deploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);
        return deployService.deploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "deploying")));
    }

    // POST /api/deploy/{id}/redeploy — передеплоить (OWNER)
    @PostMapping("/{id}/redeploy")
    public CompletableFuture<ResponseEntity<Map<String, String>>> redeploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);
        return deployService.redeploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "redeploying")));
    }

    // DELETE /api/deploy/{id} — удалить с ноды (OWNER)
    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> undeploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.OWNER);
        return deployService.undeploy(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "undeployed")));
    }
}