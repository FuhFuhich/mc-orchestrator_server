package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.model.RefreshToken;
import com.example.mine_com_server.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${app.refresh-token.expiration-days:30}")
    private int expirationDays;

    private final RefreshTokenRepository refreshTokenRepository;

    // ===== СОЗДАТЬ =====

    @Transactional
    public String create(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);

        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(expirationDays))
                .build();

        return refreshTokenRepository.save(token).getToken();
    }

    // ===== ВАЛИДИРОВАТЬ И ВЕРНУТЬ userId =====

    @Transactional
    public UUID validate(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new ForbiddenException("Невалидный refresh token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new ForbiddenException("Refresh token истёк, войдите снова");
        }

        return token.getUserId();
    }

    // ===== ОТОЗВАТЬ (при logout) =====

    @Transactional
    public void revokeByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // ===== ОЧИСТКА ПРОСРОЧЕННЫХ (раз в сутки) =====

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("[REFRESH] Очистка просроченных токенов выполнена");
    }
}