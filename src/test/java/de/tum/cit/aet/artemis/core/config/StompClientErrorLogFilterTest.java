package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class StompClientErrorLogFilterTest {

    private static final String SUB_PROTOCOL_HANDLER = "org.springframework.web.socket.messaging.StompSubProtocolHandler";

    private static final String BROKER_RELAY = "org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler";

    private final LoggerContext context = new LoggerContext();

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        var filter = new StompClientErrorLogFilter();
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    private Logger logger(String name) {
        return context.getLogger(name);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Received ERROR {message=[There already is a subscription for: /topic/communication/courses/1. Either use unique subscription IDs]} session=a, user=u",
            "Received ERROR {message=[Cannot unsubscribe as no subscription exists for id: abc]} session=a, user=u",
            "Failed to forward SUBSCRIBE /topic/user/1/notifications id=/topic/user/1/notifications session=a", "Failed to forward DISCONNECT session=a" })
    void logsTheMisuseOfTheBrokerByAClientAsWarning(String message) {
        logger(BROKER_RELAY).error(message);

        var event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo(message);
        assertThat(event.getLoggerName()).isEqualTo(BROKER_RELAY);
    }

    @Test
    void logsAFrameAfterTheEndOfItsSessionAsWarningAndKeepsTheException() {
        var exception = new IllegalStateException("No decoder for session id 'a'");

        logger(SUB_PROTOCOL_HANDLER).error("Failed to parse TextMessage payload=[SUBSCRIBE ..], byteCount=100, last=true] in session a. Sending STOMP ERROR to client.", exception);

        var event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo(exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "Transport failure: java.net.SocketException: Connection reset", "TCP connection failure in session a: Connection refused",
            "Received ERROR {message=[Some other error of the broker]} session=a, user=u" })
    void keepsOtherErrorsOfTheBrokerRelayAsError(String message) {
        logger(BROKER_RELAY).error(message);

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void keepsAnUnparsableFrameAsErrorUnlessTheSessionEnded() {
        logger(SUB_PROTOCOL_HANDLER).error("Failed to parse TextMessage payload=[SUBSCRIBE ..] in session a.", new IllegalStateException("Another problem"));
        logger(SUB_PROTOCOL_HANDLER).error("Failed to parse TextMessage payload=[SUBSCRIBE ..] in session a.", new IllegalArgumentException("No decoder for session id 'a'"));
        logger(SUB_PROTOCOL_HANDLER).error("Failed to parse TextMessage payload=[SUBSCRIBE ..] in session a.");
        logger(SUB_PROTOCOL_HANDLER).error("Another message", new IllegalStateException("No decoder for session id 'a'"));

        assertThat(appender.list).hasSize(4).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void doesNotChangeTheSameMessageOfAnotherLogger() {
        logger("de.tum.cit.aet.artemis.SomeService").error("Failed to forward DISCONNECT session=a");

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void doesNotChangeTheLevelOfOtherLevels() {
        logger(BROKER_RELAY).info("Failed to forward DISCONNECT session=a");

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void keepsTheErrorWhenWarningsAreNotLogged() {
        logger(BROKER_RELAY).setLevel(Level.ERROR);

        logger(BROKER_RELAY).error("Failed to forward DISCONNECT session=a");

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }
}
