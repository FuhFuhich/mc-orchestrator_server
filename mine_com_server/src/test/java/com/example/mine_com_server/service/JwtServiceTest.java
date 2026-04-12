package com.example.mine_com_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);
    }

    @Test
    void generateToken_and_validate_successfully() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId);

        UserDetails userDetails = User.withUsername(userId.toString())
                .password("ignored")
                .authorities("ROLE_USER")
                .build();

        assertNotNull(token);
        assertEquals(userId.toString(), jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_returnsFalse_forAnotherUser() {
        UUID tokenUserId = UUID.randomUUID();
        String token = jwtService.generateToken(tokenUserId);

        UserDetails anotherUser = User.withUsername(UUID.randomUUID().toString())
                .password("ignored")
                .authorities("ROLE_USER")
                .build();

        assertFalse(jwtService.isTokenValid(token, anotherUser));
    }

    @Test
    void generateModsShareToken_isValid_forSameMinecraftServer() {
        UUID mcServerId = UUID.randomUUID();
        String token = jwtService.generateModsShareToken(mcServerId, 60_000L);

        assertTrue(jwtService.isModsShareTokenValid(token, mcServerId));
    }

    @Test
    void generateModsShareToken_isInvalid_forAnotherMinecraftServer() {
        UUID mcServerId = UUID.randomUUID();
        String token = jwtService.generateModsShareToken(mcServerId, 60_000L);

        assertFalse(jwtService.isModsShareTokenValid(token, UUID.randomUUID()));
    }

    @Test
    void generateModsShareToken_isInvalid_whenExpired() throws InterruptedException {
        UUID mcServerId = UUID.randomUUID();
        String token = jwtService.generateModsShareToken(mcServerId, 5L);

        Thread.sleep(15L);

        assertFalse(jwtService.isModsShareTokenValid(token, mcServerId));
    }

    @Test
    void generateToken_throwsException_whenSecretTooShort() {
        ReflectionTestUtils.setField(jwtService, "secret", "short-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jwtService.generateToken(UUID.randomUUID()));

        assertTrue(ex.getMessage().contains("JWT secret"));
    }
}
