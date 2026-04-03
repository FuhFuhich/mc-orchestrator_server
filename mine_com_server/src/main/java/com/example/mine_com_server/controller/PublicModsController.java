package com.example.mine_com_server.controller;

import com.example.mine_com_server.service.JwtService;
import com.example.mine_com_server.service.MinecraftServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/public/mc-servers")
@RequiredArgsConstructor
public class PublicModsController {

    private final MinecraftServerService mcServerService;
    private final JwtService jwtService;

    @GetMapping("/{id}/mods/download")
    public ResponseEntity<byte[]> downloadModsArchive(
            @PathVariable UUID id,
            @RequestParam String token
    ) {
        if (!jwtService.isModsShareTokenValid(token, id)) {
            throw new ResponseStatusException(FORBIDDEN, "Недействительная или просроченная ссылка");
        }

        String serverName = mcServerService.findOrThrow(id).getName();
        byte[] archive = mcServerService.buildModsArchive(id);
        String filename = sanitizeFilename(serverName) + "-mods.zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(archive);
    }

    private String sanitizeFilename(String value) {
        return String.valueOf(value == null ? "server" : value)
                .replaceAll("[^\\p{L}\\p{N}._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase();
    }
}
