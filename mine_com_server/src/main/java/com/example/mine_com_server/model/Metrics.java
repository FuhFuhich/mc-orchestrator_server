package com.example.mine_com_server.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "metrics",
        indexes = @Index(name = "idx_metrics_server_time", columnList = "minecraft_server_id, recorded_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Metrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minecraft_server_id", nullable = false)
    private MinecraftServer minecraftServer;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "cpu_usage_percent", precision = 5, scale = 2)
    private BigDecimal cpuUsagePercent;

    @Column(name = "disk_usage_percent", precision = 5, scale = 2)
    private BigDecimal diskUsagePercent;

    @Column(name = "ram_usage_percent", precision = 5, scale = 2)
    private BigDecimal ramUsagePercent;

    @Column(name = "ram_used_mb")
    private Integer ramUsedMb;

    @Column(name = "ram_total_mb")
    private Integer ramTotalMb;

    @Column(name = "disk_total_mb")
    private Integer diskTotalMb;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "players_online")
    private Integer playersOnline;

    @Column(name = "total_backups")
    private Integer totalBackups;

    @Column(name = "crashes_last_24h")
    private Short crashesLast24h;

    @Column(name = "backups_size_mb_total")
    private Integer backupsSizeMbTotal;

    @Column(name = "disk_used_world_mb")
    private Integer diskUsedWorldMb;

    @Column(name = "network_rx_mb", precision = 14, scale = 2)
    private BigDecimal networkRxMb;

    @Column(name = "network_tx_mb", precision = 14, scale = 2)
    private BigDecimal networkTxMb;

    @Column(name = "container_restarts")
    private Integer containerRestarts;

    @Column(name = "tps", precision = 4, scale = 2)
    private BigDecimal tps;

    @Column(name = "mspt", precision = 5, scale = 2)
    private BigDecimal mspt;

    @Column(name = "chunks_loaded")
    private Integer chunksLoaded;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
