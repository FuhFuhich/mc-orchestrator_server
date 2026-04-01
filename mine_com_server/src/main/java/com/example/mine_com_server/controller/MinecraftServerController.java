package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.request.MinecraftServerRequest;
import com.example.mine_com_server.dto.request.RconRequest;
import com.example.mine_com_server.dto.response.MinecraftServerResponse;
import com.example.mine_com_server.dto.response.RconResponse;
import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mc-servers")
@RequiredArgsConstructor
public class MinecraftServerController {

    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;
    private final BackupService backupService;
    private final RconService rconService;

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
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.USER);
        return ResponseEntity.ok(mcServerService.getById(id));
    }

    @GetMapping("/node/{nodeId}")
    public ResponseEntity<List<MinecraftServerResponse>> getByNode(
            @PathVariable UUID nodeId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, nodeId, NodeRole.USER);
        return ResponseEntity.ok(mcServerService.getAllByNode(nodeId));
    }

    @PostMapping
    public ResponseEntity<MinecraftServerResponse> create(
            @RequestBody @Valid MinecraftServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, request.getNodeId(), NodeRole.MANAGER);

        MinecraftServerResponse response = mcServerService.create(request);
        auditService.record(userId, AuditActions.CREATE_SERVER, "MC_SERVER", response.getId(),
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinecraftServerResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid MinecraftServerRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        MinecraftServerResponse response = mcServerService.update(id, request);
        auditService.record(userId, AuditActions.UPDATE_SERVER, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteFullyAsync(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.MANAGER);

        auditService.record(userId, AuditActions.DELETE_SERVER, "MC_SERVER", id,
                "mode=full", httpRequest.getRemoteAddr());
        mcServerService.deleteFullyAsync(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "deleting",
                "mode", "full",
                "description", "Server data and DB record will be removed"
        ));
    }

    @DeleteMapping("/{id}/device")
    public ResponseEntity<Map<String, String>> deleteFromDeviceAsync(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.MANAGER);

        auditService.record(userId, AuditActions.DELETE_SERVER_DEVICE_ONLY, "MC_SERVER", id,
                "mode=device_only", httpRequest.getRemoteAddr());
        mcServerService.deleteFromDeviceAsync(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "deleting",
                "mode", "device_only",
                "description", "Server data removed from device; DB record kept (status will be 'undeployed')"
        ));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        auditService.record(userId, AuditActions.START_SERVER, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        mcServerService.startAsync(id);
        return ResponseEntity.ok(Map.of("status", "starting"));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stop(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        auditService.record(userId, AuditActions.STOP_SERVER, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        mcServerService.stopAsync(id);
        return ResponseEntity.ok(Map.of("status", "stopping"));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Map<String, String>> restart(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        auditService.record(userId, AuditActions.RESTART_SERVER, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        mcServerService.restartAsync(id);
        return ResponseEntity.ok(Map.of("status", "restarting"));
    }

    @PostMapping("/{id}/redeploy")
    public ResponseEntity<Map<String, String>> redeploy(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.MANAGER);

        auditService.record(userId, AuditActions.REDEPLOY_SERVER, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        mcServerService.redeployAsync(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "redeploying",
                "description", "Server will be stopped and freshly deployed"
        ));
    }

    @GetMapping("/{id}/console")
    public ResponseEntity<List<String>> getConsoleLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "100") int lines,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.USER);
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
        mcServerService.sendCommand(id, command);

        auditService.record(userId, AuditActions.SEND_COMMAND, "MC_SERVER", id,
                "console_command_sent", httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("status", "sent"));
    }

    @PostMapping("/{id}/rcon")
    public ResponseEntity<RconResponse> sendRconCommand(
            @PathVariable UUID id,
            @RequestBody @Valid RconRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        try {
            String response = rconService.sendCommand(id, request.getCommand());
            auditService.record(userId, AuditActions.RCON_COMMAND, "MC_SERVER", id,
                    "cmd=" + request.getCommand(), httpRequest.getRemoteAddr());
            return ResponseEntity.ok(RconResponse.ok(response));
        } catch (RconService.RconException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(RconResponse.fail(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(RconResponse.fail(e.getMessage()));
        }
    }

    @PostMapping("/{id}/backup")
    public ResponseEntity<Map<String, String>> backup(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        auditService.record(userId, AuditActions.CREATE_BACKUP, "MC_SERVER", id,
                null, httpRequest.getRemoteAddr());
        backupService.createBackup(id);
        return ResponseEntity.ok(Map.of("status", "backup_started"));
    }

    @PostMapping(path = "/{id}/archives", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadArchive(
            @PathVariable UUID id,
            @RequestParam("type") String type,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        String remotePath = mcServerService.uploadArchive(id, file, type);
        auditService.record(userId, AuditActions.UPDATE_SERVER, "MC_SERVER", id,
                "archive_type=" + type, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("status", "uploaded", "remotePath", remotePath));
    }
}
