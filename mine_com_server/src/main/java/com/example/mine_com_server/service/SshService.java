package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.SshException;
import com.example.mine_com_server.model.Server;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class SshService {

    private static final int TIMEOUT_MS = 30_000;
    private static final int CONNECT_TIMEOUT_MS = 30_000;

    private final Map<UUID, Session>     sessionCache   = new ConcurrentHashMap<>();
    private final Map<UUID, ChannelExec> streamChannels = new ConcurrentHashMap<>();

    // ===== ПОДКЛЮЧЕНИЕ =====

    private Session buildSession(Server server) throws JSchException {
        JSch jsch = new JSch();

        if ("key".equalsIgnoreCase(server.getAuthType())) {
            byte[] keyBytes = server.getSshPrivateKey().getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("key", keyBytes, null, null);
        }

        Session session = jsch.getSession(
                server.getSshUser(),
                server.getIpAddress(),
                server.getSshPort()
        );

        if ("password".equalsIgnoreCase(server.getAuthType())) {
            session.setPassword(server.getSshPassword());
        }

        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications",
                "key".equalsIgnoreCase(server.getAuthType()) ? "publickey" : "password");
        session.setConfig("server_host_key",
                "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
        session.setConfig("kex",
                "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256," +
                        "diffie-hellman-group14-sha256,diffie-hellman-group14-sha1");
        session.setConfig("cipher.s2c",
                "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com");
        session.setConfig("cipher.c2s",
                "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com");
        session.setConfig("mac.s2c", "hmac-sha2-256,hmac-sha2-512,hmac-sha1");
        session.setConfig("mac.c2s", "hmac-sha2-256,hmac-sha2-512,hmac-sha1");
        session.setServerAliveInterval(30_000);
        session.setServerAliveCountMax(3);
        session.setTimeout(TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private Session getOrCreateSession(Server server) throws JSchException {
        Session cached = sessionCache.get(server.getId());
        if (cached != null && cached.isConnected()) {
            return cached;
        }
        log.debug("SSH сессия для {} не активна, создаём новую", server.getIpAddress());
        Session session = buildSession(server);
        sessionCache.put(server.getId(), session);
        return session;
    }

    // ===== ВЫПОЛНЕНИЕ КОМАНДЫ (синхронно) =====

    public String execute(Server server, String command) {
        ChannelExec channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect(TIMEOUT_MS);

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }

            int exitCode = channel.getExitStatus();
            if (exitCode != 0) {
                log.warn("SSH команда завершилась с кодом {}: {}", exitCode, command);
            }

            return output.toString().trim();

        } catch (Exception e) {
            sessionCache.remove(server.getId());
            log.error("Ошибка выполнения SSH команды на {}: {}", server.getIpAddress(), e.getMessage());
            throw new SshException("Ошибка SSH команды: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    // ===== ВЫПОЛНЕНИЕ КОМАНДЫ (асинхронно) =====

    @Async("mc-async-")
    public CompletableFuture<String> executeAsync(Server server, String command) {
        return CompletableFuture.completedFuture(execute(server, command));
    }

    // ===== СТРИМИНГ ЛОГОВ С ВОЗМОЖНОСТЬЮ ОСТАНОВКИ =====

    @Async("mc-async-")
    public CompletableFuture<Void> streamLogs(Server server, UUID streamKey,
                                              String command, Consumer<String> lineConsumer) {
        ChannelExec channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect(TIMEOUT_MS);

            streamChannels.put(streamKey, channel);
            log.info("[STREAM] Стриминг {} запущен", streamKey);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null && channel.isConnected()) {
                lineConsumer.accept(line);
            }

        } catch (Exception e) {
            log.error("Ошибка стриминга логов с {}: {}", server.getIpAddress(), e.getMessage());
        } finally {
            streamChannels.remove(streamKey);
            if (channel != null && channel.isConnected()) channel.disconnect();
            log.info("[STREAM] Стриминг {} завершён", streamKey);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ===== ОСТАНОВИТЬ СТРИМИНГ =====

    public void stopStream(UUID streamKey) {
        ChannelExec channel = streamChannels.remove(streamKey);
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
            log.info("[STREAM] Стриминг {} остановлен", streamKey);
        }
    }

    // ===== ПРОВЕРИТЬ АКТИВЕН ЛИ СТРИМИНГ =====

    public boolean isStreaming(UUID streamKey) {
        ChannelExec ch = streamChannels.get(streamKey);
        return ch != null && ch.isConnected();
    }

    // ===== ЗАГРУЗКА ФАЙЛА НА СЕРВЕР (SFTP) =====

    public void uploadFile(Server server, String localPath, String remotePath) {
        ChannelSftp channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);

            String remoteDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
            String[] parts = remoteDir.split("/");
            StringBuilder currentPath = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                currentPath.append("/").append(part);
                try { channel.mkdir(currentPath.toString()); } catch (SftpException ignored) {}
            }

            channel.put(localPath, remotePath);
            log.info("Файл загружен: {} → {}:{}", localPath, server.getIpAddress(), remotePath);

        } catch (Exception e) {
            sessionCache.remove(server.getId());
            log.error("Ошибка SFTP загрузки на {}: {}", server.getIpAddress(), e.getMessage());
            throw new SshException("Ошибка SFTP: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    // ===== СКАЧАТЬ ФАЙЛ В ПАМЯТЬ (SFTP) =====

    public byte[] downloadFile(Server server, String remotePath) {
        ChannelSftp channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);

            InputStream stream = channel.get(remotePath);
            return stream.readAllBytes();

        } catch (Exception e) {
            sessionCache.remove(server.getId());
            log.error("Ошибка SFTP скачивания с {}: {}", server.getIpAddress(), e.getMessage());
            throw new SshException("Ошибка SFTP скачивания: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
        }
    }

    // ===== ПРОВЕРКА ДОСТУПНОСТИ НОДЫ =====

    public boolean isReachable(Server server) {
        try {
            getOrCreateSession(server);
            return true;
        } catch (Exception e) {
            log.warn("Нода {} недоступна: {}", server.getIpAddress(), e.getMessage());
            return false;
        }
    }

    // ===== ЯВНОЕ ЗАКРЫТИЕ СЕССИИ =====

    public void closeSession(UUID serverId) {
        stopStream(serverId); // на всякий случай останавливаем стриминг
        Session session = sessionCache.remove(serverId);
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("SSH сессия закрыта для ноды {}", serverId);
        }
    }
}