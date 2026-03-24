package com.example.mine_com_server.dto.response;

import com.example.mine_com_server.model.NodeHardware;
import com.example.mine_com_server.model.NodeHardware.DiskInfo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class NodeHardwareResponse {

    private UUID id;
    private UUID nodeId;
    private String nodeName;

    private String cpuModel;
    private Integer cpuCores;
    private Integer cpuThreads;
    private Double cpuMhz;

    private Long ramTotalMb;
    private Long ramAvailableMb;

    private List<DiskInfo> disks;

    private String osName;
    private String osVersion;
    private String kernel;

    private String gpuModel;

    private LocalDateTime scannedAt;

    public static NodeHardwareResponse from(NodeHardware hw) {
        NodeHardwareResponse response = new NodeHardwareResponse();
        response.setId(hw.getId());
        response.setNodeId(hw.getNode().getId());
        response.setNodeName(hw.getNode().getName());
        response.setCpuModel(hw.getCpuModel());
        response.setCpuCores(hw.getCpuCores());
        response.setCpuThreads(hw.getCpuThreads());
        response.setCpuMhz(hw.getCpuMhz());
        response.setRamTotalMb(hw.getRamTotalMb());
        response.setRamAvailableMb(hw.getRamAvailableMb());
        response.setDisks(hw.getDisks());
        response.setOsName(hw.getOsName());
        response.setOsVersion(hw.getOsVersion());
        response.setKernel(hw.getKernel());
        response.setGpuModel(hw.getGpuModel());
        response.setScannedAt(hw.getScannedAt());
        return response;
    }
}