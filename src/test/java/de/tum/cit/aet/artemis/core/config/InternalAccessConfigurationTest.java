package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.ArtemisApp;

class InternalAccessConfigurationTest {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(InternalAccessConfiguration.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void warnsInProductionWhenEveryAddressIsAllowed() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs[0]", "::/0")
                .withProperty("artemis.security.internal.allowed-cidrs[1]", "0.0.0.0/0");
        environment.setActiveProfiles("prod");

        InternalAccessConfiguration.warnIfFullyOpen(environment);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("artemis.security.internal.allowed-cidrs");
    }

    @Test
    void staysQuietInProductionWhenTheListIsNarrowed() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs[0]", "10.0.0.0/24");
        environment.setActiveProfiles("prod");

        InternalAccessConfiguration.warnIfFullyOpen(environment);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void staysQuietOutsideProduction() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs[0]", "0.0.0.0/0");
        environment.setActiveProfiles("dev");

        InternalAccessConfiguration.warnIfFullyOpen(environment);

        assertThat(appender.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.0.0.0/0", "::0/0", "0:0:0:0:0:0:0:0/0", "192.168.1.0/00" })
    void warnsInProductionForEveryRangeWithPrefixLengthZero(String range) {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs[0]", "10.0.0.0/24")
                .withProperty("artemis.security.internal.allowed-cidrs[1]", range);
        environment.setActiveProfiles("prod");

        InternalAccessConfiguration.warnIfFullyOpen(environment);

        assertThat(appender.list).hasSize(1);
    }

    @Test
    void staysQuietForRangesWithAPrefixLengthAboveZero() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs", "10.0.0.0/8,::1/128,fd00::/10");
        environment.setActiveProfiles("prod");

        InternalAccessConfiguration.warnIfFullyOpen(environment);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void theStartupOfTheApplicationChecksTheList() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.security.internal.allowed-cidrs[0]", "0.0.0.0/0");
        environment.setActiveProfiles("core", "prod");

        new ArtemisApp(environment).initApplication();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    }
}
