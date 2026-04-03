package com.example.mine_com_server.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class NodeUsageResponse {
    private UUID nodeId;
    private String nodeName;

    private BigDecimal cpuUsagePercent;
    private BigDecimal cpuLoadAverage1m;
    private BigDecimal cpuLoadAverage5m;
    private BigDecimal cpuLoadAverage15m;

    private Integer ramUsedMb;
    private Integer ramTotalMb;
    private BigDecimal ramUsagePercent;

    private Integer diskUsedMb;
    private Integer diskTotalMb;
    private BigDecimal diskUsagePercent;

    private BigDecimal networkRxMb;
    private BigDecimal networkTxMb;

    private Integer dockerContainersRunning;
    private Integer dockerContainersTotal;
    private LocalDateTime collectedAt;
}
