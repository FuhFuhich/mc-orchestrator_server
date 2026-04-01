package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.response.UserResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.User;
import com.example.mine_com_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserRepository userRepository;
    private final AuthService authService;

    private static final Path UPLOAD_DIR = Paths.get(
            System.getProperty("user.dir"), "uploads", "avatars"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    @Transactional
    public UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalStateException("Файл пустой");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalStateException("Файл слишком большой. Максимум 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalStateException("Разрешены только JPEG, PNG, WebP");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        try {
            Files.createDirectories(UPLOAD_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать директорию для аватаров", e);
        }

        if (user.getAvatarUrl() != null) {
            _deleteOldAvatar(user.getAvatarUrl());
        }

        String ext = _getExtension(contentType);
        String filename = userId + "_" + UUID.randomUUID() + ext;
        Path targetPath = UPLOAD_DIR.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении файла", e);
        }

        String avatarUrl = "/api/user/avatar/" + filename;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        log.info("[AVATAR] Аватар обновлён для userId={}", userId);
        return authService.getMe(userId);
    }

    private void _deleteOldAvatar(String avatarUrl) {
        try {
            String filename = avatarUrl.substring(avatarUrl.lastIndexOf('/') + 1);
            Path old = UPLOAD_DIR.resolve(filename);
            Files.deleteIfExists(old);
        } catch (Exception e) {
            log.warn("[AVATAR] Не удалось удалить старый аватар: {}", e.getMessage());
        }
    }

    private String _getExtension(String contentType) {
        return switch (contentType) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default           -> ".jpg";
        };
    }
}