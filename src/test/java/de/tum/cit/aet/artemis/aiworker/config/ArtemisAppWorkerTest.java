package de.tum.cit.aet.artemis.aiworker.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;

import de.tum.cit.aet.artemis.ArtemisApp;
import de.tum.cit.aet.artemis.core.ApplicationWebXml;
import de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver;

@Isolated("SpringApplication resets JVM-wide logging")
class ArtemisAppWorkerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class RoleTestConfiguration {
    }

    @ParameterizedTest
    @CsvSource({ "'core,buildagent',Local", "buildagent,Redis", "aiworker,Redis" })
    void validNodeRolesCompleteStartupValidation(String profiles, String provider) {
        var app = new SpringApplicationBuilder(RoleTestConfiguration.class).web(WebApplicationType.NONE).build();
        ArtemisApp.configureApplication(app);
        try (var context = app.run("--spring.profiles.active=" + profiles, "--artemis.distributed-data.provider=" + provider, "--spring.main.banner-mode=off")) {
            assertThat(context.isActive()).isTrue();
            assertThat(DistributedDataProviderResolver.resolveProvider(context.getEnvironment())).isEqualTo(provider);
        }
    }

    @Test
    void standaloneBuildAgentRejectsProcessLocalProvider() {
        assertThatThrownBy(
                () -> ArtemisApp.main(new String[] { "--spring.profiles.active=buildagent", "--artemis.distributed-data.provider=Local", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("A standalone build agent requires the Redis data provider");
    }

    @Test
    void servletContainerRejectsProcessLocalStandaloneBuildAgent() {
        class ServletDeployment extends ApplicationWebXml {

            SpringApplicationBuilder application() {
                return configure(new SpringApplicationBuilder());
            }
        }
        assertThatThrownBy(() -> new ServletDeployment().application().run("--spring.profiles.active=buildagent", "--artemis.distributed-data.provider=Local",
                "--spring.main.banner-mode=off")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("A standalone build agent requires the Redis data provider");
    }

    @Test
    void standaloneWorkerRejectsProcessLocalProvider() {
        assertThatThrownBy(
                () -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker", "--artemis.distributed-data.provider=local", "--spring.main.banner-mode=off" }))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires the Redis data provider");
    }

    @ParameterizedTest
    @ValueSource(strings = { "servlet", "reactive" })
    void standaloneWorkerRejectsHttpModeOverride(String webType) {
        assertThatThrownBy(() -> ArtemisApp.main(new String[] { "--spring.profiles.active=prod,aiworker", "--artemis.distributed-data.provider=Redis",
                "--spring.main.web-application-type=" + webType, "--spring.main.banner-mode=off" })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot enable an HTTP server");
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
