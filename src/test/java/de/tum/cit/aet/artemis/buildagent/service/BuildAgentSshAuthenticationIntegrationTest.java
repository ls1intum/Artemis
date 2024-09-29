package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class BuildAgentSshAuthenticationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    @Qualifier("hazelcastInstance")
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private BuildAgentSshKeyService buildAgentSSHKeyService;

    @Autowired
    private SharedQueueProcessingService sharedQueueProcessingService;

    @Test
    void testWriteSSHKey() {
        boolean sshPrivateKeyExists = Files.exists(Path.of(System.getProperty("java.io.tmpdir"), "id_rsa"));
        assertThat(sshPrivateKeyExists).as("SSH private key written to tmp dir.").isTrue();
    }

    @Test
    void testSSHInHazelcast() {
        sharedQueueProcessingService.updateBuildAgentInformation();
        IMap<String, BuildAgentInformation> buildAgentInformation = hazelcastInstance.getMap("buildAgentInformation");
        assertThat(buildAgentInformation.values()).as("SSH public key available in hazelcast.")
                .anyMatch(agent -> agent.publicSshKey().equals(buildAgentSSHKeyService.getPublicKeyAsString()));
    }
}
