package com.example.mine_com_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String identity;

    @NotBlank
    private String password;
}