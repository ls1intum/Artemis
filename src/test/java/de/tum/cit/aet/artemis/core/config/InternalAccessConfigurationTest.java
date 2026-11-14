package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
}
