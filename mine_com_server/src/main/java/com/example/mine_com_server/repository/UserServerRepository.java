package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.UserServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserServerRepository extends JpaRepository<UserServer, UUID> {

    List<UserServer> findAllByUserId(UUID userId);
    List<UserServer> findAllByServerId(UUID serverId);
    boolean existsByUserIdAndServerId(UUID userId, UUID serverId);
    Optional<UserServer> findByUserIdAndServerId(UUID userId, UUID serverId);
}