package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.response.FileEntryResponse;
import com.example.mine_com_server.model.AuditActions;
import com.example.mine_com_server.model.NodeRole;
import com.example.mine_com_server.service.AuditService;
import com.example.mine_com_server.service.FileSystemService;
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
@RequestMapping("/api/mc-servers/{id}/fs")
@RequiredArgsConstructor
public class FileSystemController {

    private final FileSystemService fileSystemService;
    private final MinecraftServerService mcServerService;
    private final NodeAccessService nodeAccessService;
    private final AuditService auditService;

    @GetMapping("/list")
    public ResponseEntity<List<FileEntryResponse>> listDirectory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "") String path,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);
        return ResponseEntity.ok(fileSystemService.listDirectory(id, path));
    }

    @GetMapping("/read")
    public ResponseEntity<Map<String, String>> readFile(
            @PathVariable UUID id,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.VIEWER);

        auditService.record(userId, AuditActions.FILE_READ, "MC_SERVER", id, "path=" + path, httpRequest.getRemoteAddr());
        String content = fileSystemService.readFile(id, path);
        return ResponseEntity.ok(Map.of("content", content));
    }

    @PutMapping("/write")
    public ResponseEntity<Map<String, String>> writeFile(
            @PathVariable UUID id,
            @RequestParam String path,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        String content = body.getOrDefault("content", "");
        fileSystemService.writeFile(id, path, content);
        auditService.record(userId, AuditActions.FILE_WRITE, "MC_SERVER", id, "path=" + path, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("status", "written"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(
            @PathVariable UUID id,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);

        fileSystemService.deleteFile(id, path);
        auditService.record(userId, AuditActions.FILE_DELETE, "MC_SERVER", id, "path=" + path, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/mkdir")
    public ResponseEntity<Map<String, String>> createDirectory(
            @PathVariable UUID id,
            @RequestParam String path,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        nodeAccessService.requireRole(userId, mcServerService.getNodeId(id), NodeRole.ADMIN);
        fileSystemService.createDirectory(id, path);
        return ResponseEntity.ok(Map.of("status", "created"));
    }
}
