package com.example.mine_com_server.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "users_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "dark_theme")
    private Boolean darkTheme = true;

    @Builder.Default
    @Column(length = 10)
    private String locale = "ru";

    @Builder.Default
    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;
}