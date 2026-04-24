package com.example.mine_com_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class EncryptionConfig {

    @Value("${app.encryption.key}")
    private String encryptionKey;

    @Bean
    public SecretKey aesSecretKey() {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "APP_ENCRYPTION_KEY должен быть ровно 32 байта (символа) для AES-256, фактически: "
                            + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}