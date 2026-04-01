package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.SshException;
import com.example.mine_com_server.model.Server;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class SshService {

    private static final int TIMEOUT_MS = 120_000;
    private static final int CONNECT_TIMEOUT_MS = 60_000;

    private final Map<UUID, Session> sessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, ChannelExec> streamChannels = new ConcurrentHashMap<>();

    private Session buildSession(Server server) throws JSchException {
        JSch jsch = new JSch();

        if ("key".equalsIgnoreCase(server.getAuthType())) {
            byte[] keyBytes = server.getSshPrivateKey().getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("key", keyBytes, null, null);
        }

        Session session = jsch.getSession(server.getSshUser(), server.getIpAddress(), server.getSshPort());
        if ("password".equalsIgnoreCase(server.getAuthType())) {
            session.setPassword(server.getSshPassword());
        }

        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "key".equalsIgnoreCase(server.getAuthType()) ? "publickey" : "password");
        session.setConfig("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
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
        Session session = buildSession(server);
        sessionCache.put(server.getId(), session);
        return session;
    }

    public String execute(Server server, String command) {
        ChannelExec channel = null;
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(stderr, true);

            InputStream stdout = channel.getInputStream();
            channel.connect(TIMEOUT_MS);

            String output = readFully(stdout, channel);
            int exitCode = channel.getExitStatus();
            String errorOutput = stderr.toString(StandardCharsets.UTF_8);

            if (exitCode != 0) {
                throw new SshException(buildExitMessage(command, exitCode, output, errorOutput));
            }
            return output.trim();
        } catch (Exception e) {
            sessionCache.remove(server.getId());
            if (e instanceof SshException sshException) {
                throw sshException;
            }
            throw new SshException("Ошибка SSH команды [" + summarizeCommand(command) + "]: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public String executeScript(Server server, String scriptPath, String... args) {
        StringBuilder cmd = new StringBuilder("bash ").append(quote(scriptPath));
        for (String arg : args) {
            cmd.append(' ').append(quote(arg));
        }
        return execute(server, cmd.toString());
    }

    public String runScript(Server server, String mcRoot, String scriptPath, String... args) {
        StringBuilder cmd = new StringBuilder();
        if (mcRoot != null && !mcRoot.isBlank()) {
            cmd.append("MC_ROOT=").append(quote(mcRoot)).append(' ');
        }
        cmd.append("bash ").append(quote(scriptPath));
        for (String arg : args) {
            cmd.append(' ').append(quote(arg));
        }
        return execute(server, cmd.toString());
    }

    public String quote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    @Async("mc-async-")
    public CompletableFuture<String> executeAsync(Server server, String command) {
        return CompletableFuture.completedFuture(execute(server, command));
    }

    @Async("mc-async-")
    public CompletableFuture<Void> streamLogs(Server server, UUID streamKey, String command, Consumer<String> lineConsumer) {
        ChannelExec channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect(TIMEOUT_MS);
            streamChannels.put(streamKey, channel);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null && channel.isConnected()) {
                lineConsumer.accept(line);
            }
        } catch (Exception e) {
            log.error("Ошибка стриминга логов с {}: {}", server.getIpAddress(), e.getMessage(), e);
        } finally {
            streamChannels.remove(streamKey);
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public void stopStream(UUID streamKey) {
        ChannelExec channel = streamChannels.remove(streamKey);
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }

    public boolean isStreaming(UUID streamKey) {
        ChannelExec ch = streamChannels.get(streamKey);
        return ch != null && ch.isConnected();
    }

    public void uploadFile(Server server, String localPath, String remotePath) {
        ChannelSftp channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);
            ensureRemoteDir(channel, remotePath.substring(0, remotePath.lastIndexOf('/')));
            channel.put(localPath, remotePath, ChannelSftp.OVERWRITE);
        } catch (Exception e) {
            sessionCache.remove(server.getId());
            throw new SshException("Ошибка SFTP: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public void uploadBytes(Server server, byte[] bytes, String remotePath) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("mc-upload-", ".bin");
            Files.write(tmp, bytes);
            uploadFile(server, tmp.toString(), remotePath);
        } catch (IOException e) {
            throw new SshException("Ошибка подготовки файла к загрузке: " + e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public byte[] downloadFile(Server server, String remotePath) {
        ChannelSftp channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);
            try (InputStream stream = channel.get(remotePath)) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            sessionCache.remove(server.getId());
            throw new SshException("Ошибка SFTP скачивания: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public boolean exists(Server server, String remotePath) {
        ChannelSftp channel = null;
        try {
            Session session = getOrCreateSession(server);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(TIMEOUT_MS);
            channel.lstat(remotePath);
            return true;
        } catch (SftpException e) {
            return false;
        } catch (Exception e) {
            sessionCache.remove(server.getId());
            throw new SshException("Ошибка проверки файла по SFTP: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    public boolean isReachable(Server server) {
        try {
            getOrCreateSession(server);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> pingTcp(Server server) {
        long start = System.nanoTime();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(server.getIpAddress(), server.getSshPort()), 5000);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return Map.of("reachable", true, "ms", Math.max(ms, 1));
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return Map.of("reachable", false, "ms", ms);
        }
    }

    public void closeSession(UUID serverId) {
        stopStream(serverId);
        Session session = sessionCache.remove(serverId);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private void ensureRemoteDir(ChannelSftp channel, String remoteDir) throws SftpException {
        String[] parts = remoteDir.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            currentPath.append('/').append(part);
            try {
                channel.mkdir(currentPath.toString());
            } catch (SftpException ignored) {
            }
        }
    }

    private String readFully(InputStream in, ChannelExec channel) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        while (true) {
            while (in.available() > 0) {
                int read = in.read(buffer, 0, buffer.length);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
            }

            if (channel.isClosed()) {
                while (in.available() > 0) {
                    int read = in.read(buffer, 0, buffer.length);
                    if (read < 0) {
                        break;
                    }
                    out.write(buffer, 0, read);
                }
                break;
            }
            Thread.sleep(50L);
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    private String buildExitMessage(String command, int exitCode, String stdout, String stderr) {
        String stdoutTail = tail(stdout, 20);
        String stderrTail = tail(stderr, 20);
        StringBuilder message = new StringBuilder("SSH команда [")
                .append(summarizeCommand(command))
                .append("] завершилась с кодом ")
                .append(exitCode);

        if (!stderrTail.isBlank()) {
            message.append(" | stderr: ").append(stderrTail);
        }
        if (!stdoutTail.isBlank()) {
            message.append(" | stdout: ").append(stdoutTail);
        }
        return message.toString();
    }

    private String summarizeCommand(String command) {
        if (command == null) {
            return "<null>";
        }
        String normalized = command.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200) + "...";
    }

    private String tail(String value, int maxLines) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] lines = value.strip().split("\\R");
        int from = Math.max(0, lines.length - maxLines);
        return String.join(" | ", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }
}
