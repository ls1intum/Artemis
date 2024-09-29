package de.tum.cit.aet.artemis.programming.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.service.BuildAgentSshKeyService;
import de.tum.cit.aet.artemis.buildagent.service.SharedQueueProcessingService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class BuildAgentSshAuthenticationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BuildAgentSshKeyService buildAgentSSHKeyService;

    @Autowired
    private SharedQueueProcessingService sharedQueueProcessingService;

    @Value("${artemis.version-control.ssh-private-key-folder-path}")
    protected String gitSshPrivateKeyPath;

    @Test
    void testWriteSSHKey() {
        boolean sshPrivateKeyExists = Files.exists(Path.of(System.getProperty("java.io.tmpdir"), "id_rsa"));
        assertThat(sshPrivateKeyExists).as("SSH private key written to tmp dir.").isTrue();
    }

    @Test
    void testSSHInHazelcast() {
        sharedQueueProcessingService.updateBuildAgentInformation();
        Map<String, BuildAgentInformation> buildAgentInformation = redissonClient.getMap("buildAgentInformation");
        assertThat(buildAgentInformation.values()).as("SSH public key available in hazelcast.")
                .anyMatch(agent -> agent.publicSshKey().equals(buildAgentSSHKeyService.getPublicKeyAsString()));
    }
}
