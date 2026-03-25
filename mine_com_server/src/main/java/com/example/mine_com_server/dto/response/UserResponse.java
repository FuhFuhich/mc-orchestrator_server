package com.example.mine_com_server.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String phoneNumber;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private String avatarUrl;
}