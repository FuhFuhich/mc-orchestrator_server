package com.example.mine_com_server.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MinecraftServerResponse {
    private UUID id;
    private UUID nodeId;
    private String nodeName;
    private String name;
    private String minecraftVersion;
    private String modLoader;
    private String modLoaderVersion;
    private String deployTarget;
    private String status;
    private Integer gamePort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer ramMb;
    private Integer cpuCores;
    private Integer diskMb;
    private Boolean autoRestart;
    private Boolean backupEnabled;
    private Integer backupIntervalHours;
    private Boolean whitelistEnabled;
    private Boolean rconEnabled;
    private Integer rconPort;
}