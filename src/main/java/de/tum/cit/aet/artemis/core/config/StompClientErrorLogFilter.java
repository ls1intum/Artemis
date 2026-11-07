package de.tum.cit.aet.artemis.core.config;

import java.util.List;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Logs the errors that a STOMP client causes itself as warnings instead of errors.
 * <p>
 * Spring logs these at the error level, although the server did nothing wrong, and they hid real errors in the error analysis:
 * <ul>
 * <li>A client that sends a frame after its session was closed, which Spring reports as {@code No decoder for session id}.</li>
 * <li>A client that subscribes twice to the same destination with the same subscription id, or unsubscribes from an id that does not exist. The web client never
 * does either, it uses one subscription per destination and a unique id for each. The message broker answers with an error frame and closes the connection, so
 * the relay cannot forward the frames that follow on that connection, which it reports as {@code Failed to forward SUBSCRIBE} and
 * {@code Failed to forward DISCONNECT}.</li>
 * </ul>
 * Every other error of the two loggers, for example a lost connection to the message broker, stays an error.
 */
public class StompClientErrorLogFilter extends TurboFilter {

    private static final String SUB_PROTOCOL_HANDLER = "org.springframework.web.socket.messaging.StompSubProtocolHandler";

    private static final String BROKER_RELAY = "org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler";

    private static final String FAILED_TO_PARSE_FRAME = "Failed to parse TextMessage payload=";

    private static final String NO_DECODER_FOR_SESSION = "No decoder for session id";

    /** The start of the messages that the broker relay logs when a client broke the protocol. */
    private static final List<String> BROKER_RELAY_CLIENT_MISUSE = List.of("Received ERROR {message=[There already is a subscription for: ",
            "Received ERROR {message=[Cannot unsubscribe as no subscription exists for id: ", "Failed to forward SUBSCRIBE ", "Failed to forward DISCONNECT ");

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        if (!Level.ERROR.equals(level) || format == null || !isCausedByClient(logger.getName(), format, throwable) || !logger.isEnabledFor(Level.WARN)) {
            return FilterReply.NEUTRAL;
        }
        // A turbo filter cannot change the level of an event, so the event is passed on as a warning, and the error is denied. Handing the event to the appenders directly
        // does not run the filters again.
        logger.callAppenders(new LoggingEvent(StompClientErrorLogFilter.class.getName(), logger, Level.WARN, format, throwable, params));
        return FilterReply.DENY;
    }

    private static boolean isCausedByClient(String loggerName, String message, Throwable throwable) {
        return switch (loggerName) {
            case SUB_PROTOCOL_HANDLER -> message.startsWith(FAILED_TO_PARSE_FRAME) && throwable instanceof IllegalStateException && throwable.getMessage() != null
                    && throwable.getMessage().startsWith(NO_DECODER_FOR_SESSION);
            case BROKER_RELAY -> BROKER_RELAY_CLIENT_MISUSE.stream().anyMatch(message::startsWith);
            default -> false;
        };
    }
}
