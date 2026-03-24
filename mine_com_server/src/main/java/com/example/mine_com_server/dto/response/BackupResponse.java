package com.example.mine_com_server.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BackupResponse {
    private UUID id;
    private UUID minecraftServerId;
    private String minecraftServerName;
    private String fileName;
    private String remotePath;
    private Integer sizeMb;
    private String backupType;
    private LocalDateTime createdAt;
}