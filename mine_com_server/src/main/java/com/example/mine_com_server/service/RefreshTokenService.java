package com.example.mine_com_server.service;

import com.example.mine_com_server.model.RefreshToken;
import com.example.mine_com_server.model.User;
import com.example.mine_com_server.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.expiration-days:30}")
    private long refreshTokenExpirationDays;

    @Transactional
    public String create(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        String hashedToken = hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(hashedToken)
                .user(user)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    public Optional<RefreshToken> findByRawToken(String rawToken) {
        return refreshTokenRepository.findByToken(hash(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Transactional
    public void deleteByRawToken(String rawToken) {
        refreshTokenRepository.deleteByToken(hash(rawToken));
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public boolean isValid(String rawToken) {
        return findByRawToken(rawToken).isPresent();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось вычислить hash refresh token", e);
        }
    }
}