package com.example.mine_com_server.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "backups",
        indexes = @Index(name = "idx_backups_mc_server", columnList = "minecraft_server_id")
)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "minecraft_server_id", nullable = false)
    private MinecraftServer minecraftServer;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "remote_path", nullable = false)
    private String remotePath;

    @Column(name = "size_mb")
    private Integer sizeMb;

    // manual / scheduled
    @Column(name = "backup_type", nullable = false)
    private String backupType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
