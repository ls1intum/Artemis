package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.ArtemisApp;

class AllowedDockerRegistriesTest {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AllowedDockerRegistries.class);
        // another test of the same JVM may have changed the level of this logger
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
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

    @Test
    void anEntryThatNamesNoRegistryIsRefusedInsteadOfEmptyingTheList() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.continuous-integration.allowed-docker-registries", "https://");

        assertThatThrownBy(() -> AllowedDockerRegistries.from(environment)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("https://");
    }

    @Test
    void oneInvalidEntryAmongValidOnesIsRefused() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.continuous-integration.allowed-docker-registries[0]", "ghcr.io")
                .withProperty("artemis.continuous-integration.allowed-docker-registries[1]", "http:///");

        assertThatThrownBy(() -> AllowedDockerRegistries.from(environment)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankEntriesAreIgnoredButTheListStaysAsConfigured() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.continuous-integration.allowed-docker-registries", "ghcr.io, ,");

        AllowedDockerRegistries registries = AllowedDockerRegistries.from(environment);

        assertThat(registries.isEmpty()).isFalse();
    }
}
