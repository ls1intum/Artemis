package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;

class LocalCITriggerNetworkTest {

    @Test
    void synchronizationIsOfflineAndStripsExerciseCredentials() {
        DockerRunConfig original = new DockerRunConfig(List.of("SECRET=private"), "host", 2, 1024, 1024);
        DockerRunConfig restricted = LocalCITriggerService.restrictedRunConfig(original);
        assertThat(restricted.network()).isEqualTo("none");
        assertThat(restricted.env()).isEmpty();
        assertThat(restricted.cpuCount()).isEqualTo(2);
        assertThat(restricted.memory()).isEqualTo(1024);
        assertThat(restricted.memorySwap()).isEqualTo(1024);
        assertThat(original.env()).containsExactly("SECRET=private");
    }

    @Test
    void offlinePolicyAlsoAppliesWithoutExerciseOverrides() {
        assertThat(LocalCITriggerService.restrictedRunConfig(null)).isEqualTo(new DockerRunConfig(List.of(), "none", 0, 0, 0));
    }
}
