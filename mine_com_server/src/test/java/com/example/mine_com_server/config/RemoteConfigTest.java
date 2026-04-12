package com.example.mine_com_server.config;

import com.example.mine_com_server.model.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RemoteConfigTest {

    private RemoteConfig remoteConfig;
    private Server server;

    @BeforeEach
    void setUp() {
        remoteConfig = new RemoteConfig();
        ReflectionTestUtils.setField(remoteConfig, "remoteRoot", "$HOME/mc-com");
        ReflectionTestUtils.setField(remoteConfig, "dockerRamPath", "/dev/shm/mc-com/servers");

        server = Server.builder()
                .sshUser("sha")
                .build();
    }

    @Test
    void rootFor_resolvesHomeBasedPath() {
        assertEquals("/home/sha/mc-com", remoteConfig.rootFor(server));
    }

    @Test
    void scriptsDir_and_serversDir_areBuiltCorrectly() {
        assertEquals("/home/sha/mc-com/scripts", remoteConfig.scriptsDir(server));
        assertEquals("/home/sha/mc-com/servers", remoteConfig.serversDir(server));
    }

    @Test
    void dockerServerDataDir_usesSsdByDefault() {
        UUID mcId = UUID.randomUUID();

        String path = remoteConfig.dockerServerDataDir(server, mcId, null);

        assertEquals("/home/sha/mc-com/docker/servers/ssd/" + mcId, path);
    }

    @Test
    void dockerServerDataDir_usesHddDirectory_forHddStorage() {
        UUID mcId = UUID.randomUUID();

        String path = remoteConfig.dockerServerDataDir(server, mcId, "hdd");

        assertEquals("/home/sha/mc-com/docker/servers/hdd/" + mcId, path);
    }

    @Test
    void dockerServerDataDir_usesRamDirectory_forRamStorage() {
        UUID mcId = UUID.randomUUID();

        String path = remoteConfig.dockerServerDataDir(server, mcId, "ram");

        assertEquals("/dev/shm/mc-com/servers/" + mcId, path);
    }

    @Test
    void dockerBackupDir_usesCurrentImplementation_forRamStorage() {
        UUID mcId = UUID.randomUUID();

        String path = remoteConfig.dockerBackupDir(server, mcId, "ram");

        assertEquals("/home/sha/mc-com/docker/backups/ssd/" + mcId, path);
    }

    @Test
    void rootFor_usesRootHome_forRootUser() {
        Server rootServer = Server.builder()
                .sshUser("root")
                .build();

        assertEquals("/root/mc-com", remoteConfig.rootFor(rootServer));
    }

    @Test
    void rootFor_throwsException_whenSshUserMissing() {
        Server invalidServer = Server.builder()
                .sshUser("   ")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> remoteConfig.rootFor(invalidServer));

        assertTrue(ex.getMessage().contains("sshUser"));
    }
}
