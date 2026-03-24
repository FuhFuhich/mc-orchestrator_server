package com.example.mine_com_server.model;

import com.example.mine_com_server.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "servers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Builder.Default
    @Column(name = "ssh_port")
    private Integer sshPort = 22;

    @Column(name = "ssh_user", length = 50)
    private String sshUser;

    @Column(name = "auth_type", length = 10)
    private String authType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ssh_private_key", columnDefinition = "TEXT")
    private String sshPrivateKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "ssh_password", columnDefinition = "TEXT")
    private String sshPassword;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String os;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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