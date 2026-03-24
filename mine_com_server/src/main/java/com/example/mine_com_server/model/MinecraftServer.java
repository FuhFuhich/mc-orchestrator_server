package com.example.mine_com_server.model;

import com.example.mine_com_server.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "minecraft_servers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MinecraftServer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "node_id", nullable = false)
    private Server node;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "minecraft_version", length = 20)
    private String minecraftVersion;

    @Column(name = "mod_loader", length = 20)
    private String modLoader;

    @Column(name = "mod_loader_version", length = 20)
    private String modLoaderVersion;

    @Column(name = "deploy_target", length = 20)
    private String deployTarget;

    @Column(length = 20)
    private String status;

    @Builder.Default
    @Column(name = "game_port")
    private Integer gamePort = 25565;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "allocate_all_resources")
    private Boolean allocateAllResources = false;

    @Builder.Default
    @Column(name = "ram_mb")
    private Integer ramMb = 2048;

    @Builder.Default
    @Column(name = "cpu_cores")
    private Integer cpuCores = 2;

    @Column(name = "disk_mb")
    private Integer diskMb;

    @Builder.Default
    @Column(name = "auto_restart")
    private Boolean autoRestart = true;

    @Builder.Default
    @Column(name = "backup_enabled")
    private Boolean backupEnabled = true;

    @Builder.Default
    @Column(name = "backup_interval_hours")
    private Integer backupIntervalHours = 6;

    @Builder.Default
    @Column(name = "backup_auto_delete")
    private Boolean backupAutoDelete = true;

    @Builder.Default
    @Column(name = "backup_delete_after_hours")
    private Integer backupDeleteAfterHours = 168;

    @Builder.Default
    @Column(name = "whitelist_enabled")
    private Boolean whitelistEnabled = false;

    @Builder.Default
    @Column(name = "rcon_enabled")
    private Boolean rconEnabled = false;

    @Builder.Default
    @Column(name = "rcon_port")
    private Integer rconPort = 25575;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "rcon_password", columnDefinition = "TEXT")
    private String rconPassword;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}