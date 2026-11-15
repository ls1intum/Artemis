package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.ArtemisApp;

class AllowedDockerRegistriesTest {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AllowedDockerRegistries.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void theStartupOfTheApplicationLogsTheActiveList() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.continuous-integration.allowed-docker-registries[0]", "ghcr.io")
                .withProperty("artemis.continuous-integration.allowed-docker-registries[1]", "Registry.Example.org");
        environment.setActiveProfiles("core");

        new ArtemisApp(environment).initApplication();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("[ghcr.io, registry.example.org]");
    }

    @Test
    void anEmptyListAllowsEveryRegistry() {
        AllowedDockerRegistries registries = AllowedDockerRegistries.from(new MockEnvironment());

        assertThat(registries.isEmpty()).isTrue();
        assertThat(registries.allows("any.example.org/team/image")).isTrue();
    }
}
