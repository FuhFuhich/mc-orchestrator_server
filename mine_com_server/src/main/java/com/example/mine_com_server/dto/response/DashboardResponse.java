package com.example.mine_com_server.dto.response;

import lombok.Data;

@Data
public class DashboardResponse {
    private int totalNodes;
    private int onlineNodes;
    private int totalMcServers;
    private int onlineMcServers;
    private int playersOnline;
    private double avgCpuPercent;
    private double avgRamPercent;
    private double avgDiskPercent;
    private long totalBackups;
    private int crashesLast24h;
    private int offlineMcServers;
    private long backupsTotalSizeMb;
}