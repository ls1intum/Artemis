package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiWorkerApplicationTest {

    @Test
    void standaloneWorkerRejectsProcessLocalProvider() {
        assertThatThrownBy(() -> AiWorkerApplication.main(new String[] { "--artemis.distributed-data.provider=local", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a shared Hazelcast or Redis data provider");
    }

    @Test
    void standaloneHazelcastWorkerRejectsDisabledDiscovery() {
        assertThatThrownBy(() -> AiWorkerApplication.main(new String[] { "--eureka.client.enabled=false", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires Eureka discovery with Hazelcast");
    }

    @ParameterizedTest
    @ValueSource(strings = { "core", "buildagent", "localci", "localvc" })
    void standaloneWorkerRejectsServerProfiles(String profile) {
        assertThatThrownBy(() -> AiWorkerApplication.main(new String[] { "--spring.profiles.active=" + profile, "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot use Artemis server or build-agent profiles");
    }
}
