package com.example.mine_com_server.controller;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.ConsoleService;
import com.example.mine_com_server.service.MinecraftServerService;
import com.example.mine_com_server.service.NodeAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService consoleService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;

    @PostMapping("/api/console/{id}/start")
    @ResponseBody
    public ResponseEntity<Map<String, String>> startStream(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        consoleService.startStreaming(id);
        return ResponseEntity.ok(Map.of("status", "streaming"));
    }

    @PostMapping("/api/console/{id}/stop")
    @ResponseBody
    public ResponseEntity<Map<String, String>> stopStream(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        consoleService.stopStreaming(id);
        return ResponseEntity.ok(Map.of("status", "stopped"));
    }

    @PostMapping("/api/console/{id}/command")
    @ResponseBody
    public ResponseEntity<Map<String, String>> sendCommand(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        consoleService.sendCommand(id, body.get("command"));
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @GetMapping("/api/console/{id}/log")
    @ResponseBody
    public ResponseEntity<String[]> getLog(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(consoleService.getRecentLog(id, lines, offset));
    }

    @GetMapping("/api/console/{id}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> streamStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(Map.of("streaming", consoleService.isStreaming(id)));
    }

    @MessageMapping("/console/{id}/command")
    public void handleWsCommand(
            @DestinationVariable UUID id,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ForbiddenException("WebSocket аутентификация обязательна");
        }

        UUID userId = UUID.fromString(principal.getName());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        String command = payload.get("command");
        if (command != null && !command.isBlank()) {
            consoleService.sendCommand(id, command);
        }
    }
}
