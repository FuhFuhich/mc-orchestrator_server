package com.example.mine_com_server.controller;

import com.example.mine_com_server.model.AuditLog;
import com.example.mine_com_server.service.AuditService;
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

    // GET /api/audit/me?hours=24 — мои действия
    @GetMapping("/me")
    public ResponseEntity<List<AuditLog>> getMyLogs(
            @RequestParam(defaultValue = "24") int hours,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(auditService.getByUser(userId, hours));
    }

    // GET /api/audit/entity/{entityId} — история по сущности (сервер/нода)
    @GetMapping("/entity/{entityId}")
    public ResponseEntity<List<AuditLog>> getByEntity(
            @PathVariable UUID entityId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(auditService.getByEntity(entityId));
    }
}