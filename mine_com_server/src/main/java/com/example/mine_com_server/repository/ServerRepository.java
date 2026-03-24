package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServerRepository extends JpaRepository<Server, UUID> {

    List<Server> findAllByIsActiveTrue();
    boolean existsByIpAddress(String ipAddress);
}