package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;

class StompClientErrorLogFilterTest {

    private static final String SUB_PROTOCOL_HANDLER = "org.springframework.web.socket.messaging.StompSubProtocolHandler";

    private static final String BROKER_RELAY = "org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler";

    private static final String SESSION = "a2bef65d-00b5-4384-8c8f-d7a747dd7d1b";

    private static final String OTHER_SESSION = "2df1f6b5-223f-4603-8a98-e9e1328319e5";

    private static final String DUPLICATE_SUBSCRIPTION = "Received ERROR {message=[There already is a subscription for: /topic/communication/courses/1. Either use unique subscription IDs]} session="
            + SESSION + ", user=u";

    private static final String UNKNOWN_UNSUBSCRIBE = "Received ERROR {message=[Cannot unsubscribe as no subscription exists for id: abc]} session=" + SESSION + ", user=u";

    private final LoggerContext context = new LoggerContext();

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        addFilter(new StompClientErrorLogFilter());
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    private void addFilter(TurboFilter filter) {
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);
    }

    private Logger logger(String name) {
        return context.getLogger(name);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst();
    }

    @ParameterizedTest
    @ValueSource(strings = { DUPLICATE_SUBSCRIPTION, UNKNOWN_UNSUBSCRIBE })
    void logsTheRejectionOfAFrameOfAClientAsWarning(String message) {
        logger(BROKER_RELAY).error(message);

        var event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo(message);
        assertThat(event.getLoggerName()).isEqualTo(BROKER_RELAY);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Failed to forward SUBSCRIBE /topic/user/1/notifications id=/topic/user/1/notifications session=" + SESSION,
            "Failed to forward DISCONNECT session=" + SESSION })
    void logsTheFramesThatFollowARejectionOnTheSameSessionAsWarning(String message) {
        logger(BROKER_RELAY).error(DUPLICATE_SUBSCRIPTION);
        logger(BROKER_RELAY).error(message);

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.WARN);
        assertThat(appender.list.getLast().getFormattedMessage()).isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Failed to forward SUBSCRIBE /topic/user/1/notifications id=/topic/user/1/notifications session=" + SESSION,
            "Failed to forward DISCONNECT session=" + SESSION, "Failed to forward DISCONNECT" })
    void keepsAFailedForwardAsErrorWhenTheBrokerDidNotRejectItsSession(String message) {
        // another session was rejected, which says nothing about this one, and a lost connection to the broker fails the forwards of every session
        logger(BROKER_RELAY).error(DUPLICATE_SUBSCRIPTION.replace(SESSION, OTHER_SESSION));
        logger(BROKER_RELAY).error(message);

        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.ERROR);
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
            "Received ERROR {message=[Some other error of the broker]} session=" + SESSION + ", user=u" })
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
        logger("de.tum.cit.aet.artemis.SomeService").error(DUPLICATE_SUBSCRIPTION);

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void doesNotChangeTheLevelOfOtherLevels() {
        logger(BROKER_RELAY).info(DUPLICATE_SUBSCRIPTION);

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void keepsTheErrorWhenWarningsAreNotLogged() {
        logger(BROKER_RELAY).setLevel(Level.ERROR);

        logger(BROKER_RELAY).error(DUPLICATE_SUBSCRIPTION);

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void isRegisteredInTheLogbackConfiguration() throws IOException {
        try (var configuration = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(configuration).isNotNull();
            assertThat(new String(configuration.readAllBytes(), StandardCharsets.UTF_8)).contains("<turboFilter class=\"" + StompClientErrorLogFilter.class.getName() + "\"");
        }
    }

    @Test
    void keepsTheMarkerOfTheEvent() {
        Marker marker = MarkerFactory.getMarker("STOMP");

        logger(BROKER_RELAY).atError().addMarker(marker).log(DUPLICATE_SUBSCRIPTION);

        var event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMarkerList()).containsExactly(marker);
    }

    @Test
    void logsNothingWhenAnotherFilterVetoesTheWarning() {
        // vetoes the warning only: the error that the first filter replaces is not vetoed by it, as it has another level
        addFilter(new TurboFilter() {

            @Override
            public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
                return Level.WARN.equals(level) && format != null && format.contains("There already is a subscription") ? FilterReply.DENY : FilterReply.NEUTRAL;
            }
        });

        logger(BROKER_RELAY).error(DUPLICATE_SUBSCRIPTION);

        assertThat(appender.list).isEmpty();
    }
}
