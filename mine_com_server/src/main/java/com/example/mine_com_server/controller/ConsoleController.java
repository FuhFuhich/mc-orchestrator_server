package com.example.mine_com_server.controller;

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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService consoleService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;

    // POST /api/console/{id}/start — начать стриминг (VIEWER+)
    @PostMapping("/api/console/{id}/start")
    @ResponseBody
    public CompletableFuture<ResponseEntity<Map<String, String>>> startStream(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return consoleService.startStreaming(id)
                .thenApply(v -> ResponseEntity.ok(Map.of("status", "streaming")));
    }

    // POST /api/console/{id}/stop — остановить стриминг (VIEWER+)
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

    // POST /api/console/{id}/command — отправить команду (ADMIN+)
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

    // GET /api/console/{id}/log?lines=100 — последние строки (VIEWER+)
    @GetMapping("/api/console/{id}/log")
    @ResponseBody
    public ResponseEntity<String[]> getLog(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "0")   int offset,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(consoleService.getRecentLog(id, lines, offset));
    }

    // GET /api/console/{id}/status — активен ли стрим (VIEWER+)
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

    // WS: /app/console/{id}/command — команда через WebSocket (ADMIN+ проверяется в ConsoleService)
    @MessageMapping("/console/{id}/command")
    public void handleWsCommand(
            @DestinationVariable UUID id,
            @Payload Map<String, String> payload
    ) {
        String command = payload.get("command");
        if (command != null && !command.isBlank()) {
            consoleService.sendCommand(id, command);
        }
    }
}