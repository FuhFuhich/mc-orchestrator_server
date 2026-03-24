package com.example.mine_com_server.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ServerResponse {
    private UUID id;
    private String name;
    private String ipAddress;
    private Integer sshPort;
    private String sshUser;
    private String authType;
    private String description;
    private String os;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}