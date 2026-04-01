package com.example.mine_com_server.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NodeMemberResponse {
    private UUID userId;
    private String username;
    private String email;
    private String role;
    private LocalDateTime createdAt;
}