package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.ArtemisApp;

class ArtemisAppWorkerTest {

    @Test
    void standaloneWorkerRejectsProcessLocalProvider() {
        assertThatThrownBy(
                () -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker", "--artemis.distributed-data.provider=local", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a shared Hazelcast or Redis data provider");
    }

    @ParameterizedTest
    @ValueSource(strings = { "servlet", "reactive" })
    void standaloneWorkerRejectsHttpModeOverride(String webType) {
        assertThatThrownBy(() -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker", "--artemis.distributed-data.provider=Redis",
                "--spring.main.web-application-type=" + webType, "--spring.main.banner-mode=off" })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot enable an HTTP server");
    }

    @Test
    void standaloneHazelcastWorkerRejectsDisabledDiscovery() {
        assertThatThrownBy(() -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker", "--eureka.client.enabled=false", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires Eureka discovery with Hazelcast");
    }

    @ParameterizedTest
    @ValueSource(strings = { "buildagent", "localci", "localvc" })
    void standaloneWorkerRejectsServerProfiles(String profile) {
        assertThatThrownBy(() -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker," + profile, "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot use server or build-agent profiles");
    }

    @Test
    void standaloneWorkerRejectsCoreProfile() {
        assertThatThrownBy(() -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker,core", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot also run the core profile");
    }
}
