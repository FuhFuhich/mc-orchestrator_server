package com.example.mine_com_server.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "node_hardware")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NodeHardware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private Server node;

    @Column(name = "cpu_model")
    private String cpuModel;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "cpu_threads")
    private Integer cpuThreads;

    @Column(name = "cpu_mhz")
    private Double cpuMhz;

    @Column(name = "ram_total_mb")
    private Long ramTotalMb;

    @Column(name = "ram_available_mb")
    private Long ramAvailableMb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disks", columnDefinition = "jsonb")
    private List<DiskInfo> disks;

    @Column(name = "os_name")
    private String osName;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "kernel")
    private String kernel;

    @Column(name = "gpu_model")
    private String gpuModel;

    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DiskInfo {
        private String name;
        private String mount;
        private String totalGb;
        private String freeGb;
        private String type;
    }
}