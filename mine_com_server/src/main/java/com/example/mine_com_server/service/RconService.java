package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.ForbiddenException;
import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RconService {

    private static final int TYPE_RESPONSE  = 0;
    private static final int TYPE_COMMAND   = 2;
    private static final int TYPE_AUTH      = 3;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 60_000;
    private static final int MAX_RESPONSE_SIZE  = 4096;

    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private final MinecraftServerRepository mcServerRepository;

    public String sendCommand(UUID mcServerId, String command) {
        MinecraftServer mc = findOrThrow(mcServerId);
        validateRconConfig(mc);

        String host = mc.getNode().getIpAddress();
        int    port = mc.getRconPort();
        String pass = mc.getRconPassword();

        log.debug("[RCON] Sending command to {}:{} — {}", host, port, command);
        try {
            return executeRcon(host, port, pass, command);
        } catch (RconException e) {
            throw e;
        } catch (Exception e) {
            throw new RconException("RCON ошибка для сервера " + mcServerId + ": " + e.getMessage(), e);
        }
    }

    private String executeRcon(String host, int port, String password, String command)
            throws IOException {

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream  in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            int authId = nextId();
            sendPacket(out, authId, TYPE_AUTH, password);

            RconPacket authResp = readPacket(in);
            if (authResp.id() == -1) {
                throw new RconException("RCON аутентификация провалилась: неверный пароль");
            }

            int cmdId = nextId();
            sendPacket(out, cmdId, TYPE_COMMAND, command);

            RconPacket response = readPacket(in);
            return response.body();
        }
    }

    private void sendPacket(DataOutputStream out, int id, int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        int length = bodyBytes.length + 10; // 4 (id) + 4 (type) + body + 2 (nulls)

        ByteBuffer buf = ByteBuffer.allocate(4 + length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(bodyBytes);
        buf.put((byte) 0); // null-terminate body
        buf.put((byte) 0); // padding null

        out.write(buf.array());
        out.flush();
    }

    private RconPacket readPacket(DataInputStream in) throws IOException {
        ByteBuffer lenBuf = readBytes(in, 4);
        int length = lenBuf.getInt();

        if (length < 10 || length > MAX_RESPONSE_SIZE + 10) {
            throw new RconException("Некорректный размер RCON пакета: " + length);
        }

        ByteBuffer body = readBytes(in, length);
        int id   = body.getInt();
        int type = body.getInt();

        int payloadLen = length - 10;
        byte[] payload = new byte[Math.max(0, payloadLen)];
        if (payloadLen > 0) {
            body.get(payload);
        }
        if (body.remaining() >= 2) {
            body.get(); // null
            body.get(); // padding
        }

        return new RconPacket(id, type, new String(payload, StandardCharsets.UTF_8));
    }

    private ByteBuffer readBytes(DataInputStream in, int count) throws IOException {
        byte[] raw = new byte[count];
        int read = 0;
        while (read < count) {
            int n = in.read(raw, read, count - read);
            if (n < 0) throw new EOFException("RCON: stream closed unexpectedly");
            read += n;
        }
        ByteBuffer buf = ByteBuffer.wrap(raw);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        return buf;
    }

    private int nextId() {
        return requestIdCounter.incrementAndGet() & 0x7FFFFFFF;
    }

    private void validateRconConfig(MinecraftServer mc) {
        if (!Boolean.TRUE.equals(mc.getRconEnabled())) {
            throw new IllegalStateException("RCON не включён для сервера: " + mc.getId());
        }
        if (mc.getRconPort() == null || mc.getRconPort() <= 0) {
            throw new IllegalStateException("RCON порт не задан для сервера: " + mc.getId());
        }
        if (mc.getRconPassword() == null || mc.getRconPassword().isBlank()) {
            throw new IllegalStateException("RCON пароль не задан для сервера: " + mc.getId());
        }
        if (!"online".equalsIgnoreCase(mc.getStatus())) {
            throw new IllegalStateException("Сервер не запущен (статус: " + mc.getStatus() + ")");
        }
    }

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }

    private record RconPacket(int id, int type, String body) {}

    public static class RconException extends RuntimeException {
        public RconException(String message) { super(message); }
        public RconException(String message, Throwable cause) { super(message, cause); }
    }
}
