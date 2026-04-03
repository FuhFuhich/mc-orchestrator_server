package com.example.mine_com_server.service;

import com.example.mine_com_server.config.RemoteConfig;
import com.example.mine_com_server.dto.response.FileEntryResponse;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileSystemService {

    private static final long MAX_READ_SIZE_BYTES  = 2 * 1024 * 1024;
    private static final long MAX_WRITE_SIZE_BYTES = 2 * 1024 * 1024;

    private static final Set<String> PROTECTED_NAMES = Set.of(
            "start-server.sh", "run.sh",
            "runtime", "logs"
    );

    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final RemoteConfig remoteConfig;

    public List<FileEntryResponse> listDirectory(UUID mcServerId, String relativePath) {
        MinecraftServer mc = findOrThrow(mcServerId);
        String serverRoot = resolveServerRoot(mc);
        String absPath = safePath(serverRoot, relativePath);

        String raw = sshService.execute(mc.getNode(),
                "ls -la --time-style=+'%Y-%m-%d %H:%M' " + sshService.quote(absPath)
                        + " 2>/dev/null || echo '__EMPTY__'");

        if ("__EMPTY__".equals(raw.trim())) return List.of();

        return Arrays.stream(raw.split("\\R"))
                .skip(1) // skip "total N" header
                .filter(line -> !line.isEmpty())
                .map(line -> parseLsLine(line, serverRoot, absPath))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public String readFile(UUID mcServerId, String relativePath) {
        MinecraftServer mc = findOrThrow(mcServerId);
        String serverRoot = resolveServerRoot(mc);
        String absPath = safePath(serverRoot, relativePath);

        String sizeRaw = sshService.execute(mc.getNode(),
                "stat -c '%s' " + sshService.quote(absPath) + " 2>/dev/null || echo '0'");
        long size = parseLong(sizeRaw.trim());
        if (size > MAX_READ_SIZE_BYTES) {
            throw new IllegalArgumentException("Файл слишком большой для чтения через API (" +
                    size / 1024 + " KB, лимит " + MAX_READ_SIZE_BYTES / 1024 + " KB)");
        }

        byte[] data = sshService.downloadFile(mc.getNode(), absPath);
        return new String(data);
    }

    public void writeFile(UUID mcServerId, String relativePath, String content) {
        MinecraftServer mc = findOrThrow(mcServerId);
        String serverRoot = resolveServerRoot(mc);
        String absPath = safePath(serverRoot, relativePath);
        guardProtected(absPath);

        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_SIZE_BYTES) {
            throw new IllegalArgumentException("Размер записи превышает лимит " +
                    MAX_WRITE_SIZE_BYTES / 1024 + " KB");
        }

        sshService.uploadBytes(mc.getNode(), bytes, absPath);
        log.info("[FS] Файл записан: {}:{}", mcServerId, absPath);
    }

    public void deleteFile(UUID mcServerId, String relativePath) {
        MinecraftServer mc = findOrThrow(mcServerId);
        String serverRoot = resolveServerRoot(mc);
        String absPath = safePath(serverRoot, relativePath);
        guardProtected(absPath);

        sshService.execute(mc.getNode(),
                "rm -rf " + sshService.quote(absPath));
        log.info("[FS] Удалено: {}:{}", mcServerId, absPath);
    }

    public void createDirectory(UUID mcServerId, String relativePath) {
        MinecraftServer mc = findOrThrow(mcServerId);
        String serverRoot = resolveServerRoot(mc);
        String absPath = safePath(serverRoot, relativePath);
        guardProtected(absPath);

        sshService.execute(mc.getNode(),
                "mkdir -p " + sshService.quote(absPath));
        log.info("[FS] Директория создана: {}:{}", mcServerId, absPath);
    }

    private String resolveServerRoot(MinecraftServer mc) {
        return mc.isDockerMode()
                ? remoteConfig.dockerServerDataDir(mc.getNode(), mc.getId(), mc.getStorageType())
                : remoteConfig.serverDir(mc.getNode(), mc.getId());
    }

    private String safePath(String serverRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return serverRoot;

        String normalized = relativePath
                .replace('\\', '/')
                .replaceAll("\\.\\./", "")
                .replaceAll("^\\.\\.", "")
                .replaceFirst("^/+", "");

        String combined = serverRoot + "/" + normalized;

        if (!combined.startsWith(serverRoot)) {
            throw new IllegalArgumentException("Недопустимый путь: выход за пределы директории сервера");
        }
        return combined;
    }

    private void guardProtected(String absPath) {
        String name = absPath.substring(absPath.lastIndexOf('/') + 1);
        if (PROTECTED_NAMES.contains(name)) {
            throw new IllegalArgumentException(
                    "Файл или директория '" + name + "' защищена от изменения через API");
        }
    }

    private Optional<FileEntryResponse> parseLsLine(String line, String serverRoot, String parentPath) {
        try {
            String[] parts = line.trim().split("\\s+", 9);
            if (parts.length < 9) return Optional.empty();

            String perms = parts[0];
            String sizeStr = parts[4];
            String date = parts[5];
            String time = parts[6];
            String name = parts[8];

            if (".".equals(name) || "..".equals(name)) return Optional.empty();

            boolean isDir = perms.startsWith("d");
            long size;
            try { size = Long.parseLong(sizeStr); } catch (NumberFormatException e) { size = 0; }

            return Optional.of(FileEntryResponse.builder()
                    .name(name)
                    .path(toRelativePath(serverRoot, parentPath, name))
                    .directory(isDir)
                    .sizeBytes(isDir ? 0 : size)
                    .permissions(perms)
                    .lastModified(date + " " + time)
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }


    private String toRelativePath(String serverRoot, String parentPath, String name) {
        String normalizedRoot = serverRoot == null ? "" : serverRoot;
        String normalizedParent = parentPath == null ? normalizedRoot : parentPath;
        String relativeParent = normalizedParent.startsWith(normalizedRoot)
                ? normalizedParent.substring(normalizedRoot.length())
                : normalizedParent;

        String base = relativeParent == null || relativeParent.isBlank() ? "" : relativeParent.replace('\\', '/');
        if (!base.startsWith("/") && !base.isEmpty()) {
            base = "/" + base;
        }

        String candidate = (base + "/" + name).replaceAll("/+", "/");
        return candidate.startsWith("/") ? candidate : "/" + candidate;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); }
        catch (Exception e) { return 0L; }
    }

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }
}
