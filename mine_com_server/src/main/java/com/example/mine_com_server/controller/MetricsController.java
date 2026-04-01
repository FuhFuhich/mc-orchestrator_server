package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.response.MetricsResponse;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.MetricsService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;

    @GetMapping("/{mcServerId}/latest")
    public ResponseEntity<MetricsResponse> getLatest(
            @PathVariable UUID mcServerId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(mcServerId), NodeRole.VIEWER);
        return ResponseEntity.ok(metricsService.getLatest(mcServerId));
    }

    @GetMapping("/{mcServerId}/history")
    public ResponseEntity<Page<MetricsResponse>> getHistory(
            @PathVariable UUID mcServerId,
            @RequestParam(defaultValue = "24")  int hours,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(mcServerId), NodeRole.VIEWER);
        return ResponseEntity.ok(metricsService.getHistory(mcServerId, hours, page, size));
    }

    @GetMapping("/node/{nodeId}")
    public ResponseEntity<Page<MetricsResponse>> getByNode(
            @PathVariable UUID nodeId,
            @RequestParam(defaultValue = "6")  int hours,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, nodeId, NodeRole.VIEWER);
        return ResponseEntity.ok(metricsService.getByNode(nodeId, hours, page, size));
    }

    @PostMapping("/{mcServerId}/collect")
    public ResponseEntity<MetricsResponse> collectNow(
            @PathVariable UUID mcServerId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(mcServerId), NodeRole.ADMIN);
        MinecraftServer mc = mcServerService.findOrThrow(mcServerId); // нужен публичный метод
        return ResponseEntity.ok(metricsService.collectForServer(mc));
    }
}