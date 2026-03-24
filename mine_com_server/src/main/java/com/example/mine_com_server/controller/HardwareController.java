package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.response.NodeHardwareResponse;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.HardwareService;
import com.example.mine_com_server.service.NodeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class HardwareController {

    private final HardwareService hardwareService;
    private final NodeAccessService nodeAccessService;

    // POST /api/nodes/{nodeId}/scan-hardware — сканировать железо (ADMIN+)
    @PostMapping("/{nodeId}/scan-hardware")
    public ResponseEntity<NodeHardwareResponse> scan(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, nodeId, NodeRole.ADMIN);
        return ResponseEntity.ok(NodeHardwareResponse.from(hardwareService.scanAndSave(nodeId)));
    }

    // GET /api/nodes/{nodeId}/hardware — получить железо (VIEWER+)
    @GetMapping("/{nodeId}/hardware")
    public ResponseEntity<NodeHardwareResponse> get(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, nodeId, NodeRole.VIEWER);
        return ResponseEntity.ok(NodeHardwareResponse.from(hardwareService.getByNodeId(nodeId)));
    }
}