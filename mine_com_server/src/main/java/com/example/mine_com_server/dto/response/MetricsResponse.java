package com.example.mine_com_server.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MetricsResponse {
    private UUID id;
    private UUID minecraftServerId;
    private LocalDateTime recordedAt;

    private BigDecimal cpuUsagePercent;
    private BigDecimal diskUsagePercent;
    private BigDecimal ramUsagePercent;

    private Integer ramUsedMb;
    private Integer ramTotalMb;
    private Integer diskTotalMb;

    private Long uptimeSeconds;
    private Integer playersOnline;
    private Integer totalBackups;
    private Short crashesLast24h;
    private Integer backupsSizeMbTotal;
    private Integer diskUsedWorldMb;

    private BigDecimal networkRxMb;
    private BigDecimal networkTxMb;
    private Integer containerRestarts;

    private String status;
    private String storageType;

    private BigDecimal tps;
    private BigDecimal mspt;
    private Integer chunksLoaded;
}
