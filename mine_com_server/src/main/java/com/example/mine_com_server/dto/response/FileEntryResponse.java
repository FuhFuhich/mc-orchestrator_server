package com.example.mine_com_server.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileEntryResponse {
    private String name;
    private String path;
    private boolean directory;
    private long sizeBytes;
    private String permissions;
    private String lastModified;
}
