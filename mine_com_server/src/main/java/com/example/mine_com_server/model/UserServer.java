package com.example.mine_com_server.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(
        name = "users_servers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "server_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserServer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;
}