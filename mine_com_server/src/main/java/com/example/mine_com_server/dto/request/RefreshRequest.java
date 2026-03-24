package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {
    @NotBlank(message = "Refresh token обязателен")
    private String refreshToken;
}