package com.example.mine_com_server.controller;

import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.service.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    private static final Path UPLOAD_DIR = Paths.get(
            System.getProperty("user.dir"), "uploads", "avatars"
    );

    @PostMapping("/me/avatar")
    public ResponseEntity<UserResponse> uploadAvatar(
            Authentication auth,
            @RequestParam("file") MultipartFile file) {
        UUID userId = UUID.fromString(auth.getName());
        UserResponse response = avatarService.uploadAvatar(userId, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/avatar/{filename}")
    public ResponseEntity<Resource> getAvatar(
            @PathVariable String filename) throws MalformedURLException {

        // Защита от path traversal
        if (filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = UPLOAD_DIR.resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType;
        if (filename.endsWith(".png"))       contentType = "image/png";
        else if (filename.endsWith(".webp")) contentType = "image/webp";
        else                                  contentType = "image/jpeg";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}