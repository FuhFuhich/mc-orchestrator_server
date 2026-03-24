package com.example.mine_com_server.service;

import com.example.mine_com_server.exception.NotFoundException;
import com.example.mine_com_server.model.MinecraftServer;
import com.example.mine_com_server.model.Server;
import com.example.mine_com_server.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployService {

    private static final String REMOTE_SCRIPTS_DIR = "/home/sha/mc-com/scripts";
    private static final String REMOTE_SERVERS_DIR = "/home/sha/mc-com/servers";
    private static final String REMOTE_BACKUPS_DIR = "/home/sha/mc-com/backups";

    private final MinecraftServerRepository mcServerRepository;
    private final SshService sshService;
    private final MinecraftServerService mcServerService;

    // ===== ПОЛНЫЙ ДЕПЛОЙ =====

    @Async("mc-async-")
    public CompletableFuture<Void> deploy(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        try {
            log.info("[DEPLOY] Начало деплоя '{}' на ноду {}", mc.getName(), node.getIpAddress());
            setStatus(mc, "deploying");

            prepareNode(node, mc);
            setupServer(node, mc);
            applyConfig(node, mc);

            setStatus(mc, "starting");
            sshService.execute(node, buildStartCommand(mc));
            setStatus(mc, "online");

            log.info("[DEPLOY] Деплой '{}' завершён успешно", mc.getName());

        } catch (Exception e) {
            log.error("[DEPLOY] Ошибка деплоя '{}': {}", mc.getName(), e.getMessage(), e);
            setStatus(mc, "error");
            throw new RuntimeException("Ошибка деплоя: " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== РЕДЕПЛОЙ =====

    @Async("mc-async-")
    public CompletableFuture<Void> redeploy(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        try {
            log.info("[REDEPLOY] '{}' на {}", mc.getName(), node.getIpAddress());
            setStatus(mc, "deploying");

            if ("online".equals(mc.getStatus())) {
                sshService.execute(node, buildStopCommand(mc));
            }

            setupServer(node, mc);
            applyConfig(node, mc);

            sshService.execute(node, buildStartCommand(mc));
            setStatus(mc, "online");

        } catch (Exception e) {
            log.error("[REDEPLOY] Ошибка: {}", e.getMessage(), e);
            setStatus(mc, "error");
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== УДАЛЕНИЕ СЕРВЕРА С НОДЫ =====

    @Async("mc-async-")
    public CompletableFuture<Void> undeploy(UUID mcServerId) {
        MinecraftServer mc = findOrThrow(mcServerId);
        Server node = mc.getNode();

        try {
            log.info("[UNDEPLOY] '{}' с {}", mc.getName(), node.getIpAddress());

            if ("online".equals(mc.getStatus())) {
                sshService.execute(node, buildStopCommand(mc));
            }

            sshService.execute(node, "rm -rf " + REMOTE_SERVERS_DIR + "/" + mc.getId());

            if ("docker".equals(mc.getDeployTarget())) {
                sshService.execute(node, "docker rm -f mc-" + mc.getId());
            }

            setStatus(mc, "offline");
            log.info("[UNDEPLOY] '{}' удалён", mc.getName());

        } catch (Exception e) {
            log.error("[UNDEPLOY] Ошибка: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    // ===== ПОДГОТОВКА НОДЫ =====

    private void prepareNode(Server node, MinecraftServer mc) throws IOException {
        log.info("[DEPLOY] Подготовка ноды...");

        // Создаём все нужные директории в домашней папке пользователя
        sshService.execute(node, "mkdir -p " + REMOTE_SCRIPTS_DIR);
        sshService.execute(node, "mkdir -p " + REMOTE_SERVERS_DIR);
        sshService.execute(node, "mkdir -p " + REMOTE_BACKUPS_DIR);

        uploadScript(node, "install_java.sh");
        uploadScript(node, "setup_server.sh");
        uploadScript(node, "backup.sh");
        uploadScript(node, "cleanup_backups.sh");

        if ("docker".equals(mc.getDeployTarget())) {
            uploadScript(node, "install_docker.sh");
            runScript(node, "install_docker.sh");
        } else {
            uploadScript(node, "install_screen.sh");
            runScript(node, "install_java.sh");
            runScript(node, "install_screen.sh");
        }
    }

    // ===== НАСТРОЙКА СЕРВЕРА =====

    private void setupServer(Server node, MinecraftServer mc) {
        log.info("[DEPLOY] Скачивание server.jar ({}:{})...",
                mc.getModLoader(), mc.getMinecraftVersion());

        String cmd = String.format(
                "%s/setup_server.sh %s %s %s %s",
                REMOTE_SCRIPTS_DIR,
                mc.getId(),
                mc.getMinecraftVersion(),
                mc.getModLoader() != null ? mc.getModLoader() : "vanilla",
                mc.getModLoaderVersion() != null ? mc.getModLoaderVersion() : ""
        );

        String output = sshService.execute(node, cmd);
        log.info("[DEPLOY] setup_server.sh: {}", output);
    }

    // ===== ПРИМЕНЕНИЕ КОНФИГУРАЦИИ =====

    private void applyConfig(Server node, MinecraftServer mc) {
        String serverDir = REMOTE_SERVERS_DIR + "/" + mc.getId();
        String propsPath = serverDir + "/server.properties";

        StringBuilder props = new StringBuilder();
        props.append("server-port=").append(mc.getGamePort()).append("\n");
        props.append("max-players=20\n");
        props.append("online-mode=true\n");
        props.append("motd=Powered by MC-COM\n");
        props.append("view-distance=10\n");
        props.append("spawn-protection=16\n");

        if (Boolean.TRUE.equals(mc.getRconEnabled())) {
            props.append("enable-rcon=true\n");
            props.append("rcon.port=").append(mc.getRconPort()).append("\n");
            props.append("rcon.password=").append(mc.getRconPassword()).append("\n");
        }

        if (Boolean.TRUE.equals(mc.getWhitelistEnabled())) {
            props.append("white-list=true\n");
            props.append("enforce-whitelist=true\n");
        }

        String cmd = String.format(
                "cat > %s << 'EOF'\n%sEOF",
                propsPath,
                props
        );
        sshService.execute(node, cmd);
        log.info("[DEPLOY] server.properties применён");
    }

    // ===== ЗАГРУЗКА СКРИПТА НА НОДУ =====

    private void uploadScript(Server node, String scriptName) throws IOException {
        ClassPathResource resource = new ClassPathResource("scripts/" + scriptName);
        Path tmpFile = Files.createTempFile("mc-script-", ".sh");
        Files.write(tmpFile, resource.getInputStream().readAllBytes());

        sshService.execute(node, "mkdir -p " + REMOTE_SCRIPTS_DIR);

        String remotePath = REMOTE_SCRIPTS_DIR + "/" + scriptName;
        sshService.uploadFile(node, tmpFile.toString(), remotePath);
        sshService.execute(node, "chmod +x " + remotePath);

        Files.deleteIfExists(tmpFile);
        log.debug("[DEPLOY] Скрипт загружен: {}", scriptName);
    }

    // ===== ЗАПУСК СКРИПТА НА НОДЕ =====

    private void runScript(Server node, String scriptName) {
        String cmd = REMOTE_SCRIPTS_DIR + "/" + scriptName;
        log.info("[DEPLOY] Запуск скрипта: {}", scriptName);
        String output = sshService.execute(node, cmd);
        log.info("[DEPLOY] {}: {}", scriptName, output);
    }

    // ===== КОМАНДЫ START / STOP =====

    private String buildStartCommand(MinecraftServer mc) {
        String serverDir = REMOTE_SERVERS_DIR + "/" + mc.getId();

        if ("docker".equals(mc.getDeployTarget())) {
            return String.format(
                    "docker start mc-%s 2>/dev/null || docker run -d --name mc-%s " +
                            "--restart=unless-stopped " +
                            "-p %d:%d -m %dm --cpus=%s " +
                            "-v %s:/data eclipse-temurin:21 " +
                            "java -Xmx%dM -Xms512M -jar /data/server.jar nogui",
                    mc.getId(), mc.getId(),
                    mc.getGamePort(), mc.getGamePort(),
                    mc.getRamMb(), mc.getCpuCores(),
                    serverDir,
                    mc.getRamMb()
            );
        }

        return String.format(
                "cd %s && screen -dmS mc-%s java -Xmx%dM -Xms512M -jar server.jar nogui",
                serverDir, mc.getId(), mc.getRamMb()
        );
    }

    private String buildStopCommand(MinecraftServer mc) {
        if ("docker".equals(mc.getDeployTarget())) {
            return "docker stop mc-" + mc.getId();
        }
        return "screen -S mc-" + mc.getId() + " -X stuff 'stop\n' && sleep 5";
    }

    // ===== УТИЛИТЫ =====

    private void setStatus(MinecraftServer mc, String status) {
        mc.setStatus(status);
        mcServerRepository.save(mc);
    }

    private MinecraftServer findOrThrow(UUID id) {
        return mcServerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("MC-сервер не найден: " + id));
    }
}
