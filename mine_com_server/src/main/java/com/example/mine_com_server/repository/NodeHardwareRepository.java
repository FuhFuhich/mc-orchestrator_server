package com.example.mine_com_server.repository;

import com.example.mine_com_server.model.NodeHardware;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NodeHardwareRepository extends JpaRepository<NodeHardware, UUID> {
    Optional<NodeHardware> findByNodeId(UUID nodeId);
}