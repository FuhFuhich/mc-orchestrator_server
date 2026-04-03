package com.example.mine_com_server.service;

import com.example.mine_com_server.dto.response.DashboardResponse;
import com.example.mine_com_server.model.Metrics;
import com.example.mine_com_server.model.NodeMember;
import com.example.mine_com_server.repository.BackupRepository;
import com.example.mine_com_server.repository.MetricsRepository;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import com.example.mine_com_server.repository.NodeMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final NodeMemberRepository nodeMemberRepository;
    private final MinecraftServerRepository mcServerRepository;
    private final BackupRepository backupRepository;
    private final MetricsRepository metricsRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID userId) {
        List<NodeMember> memberships = nodeMemberRepository.findAllByUserId(userId);

        List<UUID> nodeIds = memberships.stream()
                .map(nm -> nm.getNode().getId())
                .toList();

        int totalNodes = nodeIds.size();

        int onlineNodes = (int) memberships.stream()
                .map(NodeMember::getNode)
                .filter(node -> Boolean.TRUE.equals(node.getIsActive()))
                .count();

        List<UUID> mcIds = mcServerRepository.findAllByNodeIdIn(nodeIds)
                .stream()
                .map(mc -> mc.getId())
                .toList();

        int totalMcServers = mcIds.size();
        int onlineMcServers = mcServerRepository.countByNodeIdInAndStatus(nodeIds, "online");

        long totalBackups = mcIds.isEmpty()
                ? 0L
                : backupRepository.countByMinecraftServerIdIn(mcIds);

        List<Metrics> latestMetrics = mcIds.stream()
                .map(metricsRepository::findTopByMinecraftServerIdOrderByRecordedAtDesc)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        int playersOnline = latestMetrics.stream()
                .mapToInt(m -> m.getPlayersOnline() != null ? m.getPlayersOnline() : 0)
                .sum();

        double avgCpuPercent = latestMetrics.stream()
                .filter(m -> m.getCpuUsagePercent() != null)
                .mapToDouble(m -> m.getCpuUsagePercent().doubleValue())
                .average()
                .orElse(0.0);

        double avgRamPercent = latestMetrics.stream()
                .filter(m -> m.getRamUsagePercent() != null)
                .mapToDouble(m -> m.getRamUsagePercent().doubleValue())
                .average()
                .orElse(0.0);

        double avgDiskPercent = latestMetrics.stream()
                .filter(m -> m.getDiskUsagePercent() != null)
                .mapToDouble(m -> m.getDiskUsagePercent().doubleValue())
                .average()
                .orElse(0.0);

        int crashesLast24h = latestMetrics.stream()
                .mapToInt(m -> m.getCrashesLast24h() != null ? m.getCrashesLast24h() : 0)
                .sum();

        DashboardResponse response = new DashboardResponse();
        response.setTotalNodes(totalNodes);
        response.setOnlineNodes(onlineNodes);
        response.setTotalMcServers(totalMcServers);
        response.setOnlineMcServers(onlineMcServers);
        response.setPlayersOnline(playersOnline);
        response.setAvgCpuPercent(Math.round(avgCpuPercent * 100.0) / 100.0);
        response.setAvgRamPercent(Math.round(avgRamPercent * 100.0) / 100.0);
        response.setAvgDiskPercent(Math.round(avgDiskPercent * 100.0) / 100.0);
        response.setTotalBackups(totalBackups);
        response.setCrashesLast24h(crashesLast24h);

        return response;
    }
}