package com.example.mine_com_server.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .claim("type", "access")
                .signWith(getKey())
                .compact();
    }

    public String generateModsShareToken(UUID mcServerId, long ttlMs) {
        return Jwts.builder()
                .subject("mods-share")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .claim("type", "mods_share")
                .claim("mcServerId", mcServerId.toString())
                .signWith(getKey())
                .compact();
    }

    public boolean isModsShareTokenValid(String token, UUID mcServerId) {
        try {
            Claims claims = extractClaims(token);
            String tokenType = claims.get("type", String.class);
            String tokenServerId = claims.get("mcServerId", String.class);
            return "mods_share".equals(tokenType)
                    && mcServerId.toString().equals(tokenServerId)
                    && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractClaims(token);
            return userDetails.getUsername().equals(claims.getSubject())
                    && "access".equals(claims.get("type", String.class))
                    && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret должен быть минимум 32 байта для HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
