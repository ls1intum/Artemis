package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class AiWorkerApplicationTest {

    @Test
    void standaloneEntryPointConfiguresNonWebWorkerProfiles() {
        var application = AiWorkerApplication.standaloneApplication();

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
        assertThat(application.getAdditionalProfiles()).containsExactlyInAnyOrder("aiworker", "aiworker-standalone");
        assertThat(application.getAllSources()).containsExactly(AiWorkerApplication.class);
        assertThat(AiWorkerApplication.class.getAnnotation(SpringBootApplication.class).scanBasePackages()).containsExactly("de.tum.cit.aet.artemis.aiworker");
    }

    @Test
    void standaloneWorkerRejectsProcessLocalProvider() {
        assertThatThrownBy(() -> AiWorkerApplication.main(new String[] { "--artemis.distributed-data.provider=local", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a shared Hazelcast or Redis data provider");
    }

    @ParameterizedTest
    @ValueSource(strings = { "servlet", "reactive" })
    void standaloneWorkerRejectsHttpModeOverride(String webType) {
        assertThatThrownBy(() -> AiWorkerApplication
                .main(new String[] { "--artemis.distributed-data.provider=Redis", "--spring.main.web-application-type=" + webType, "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot enable an HTTP server");
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
