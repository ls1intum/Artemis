package de.tum.cit.aet.artemis.core.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * does either, it uses one subscription per destination and a unique id for each, so these are other STOMP clients. The message broker answers with an error frame, which Spring
 * logs with the id of the
 * session, and closes the connection.</li>
 * <li>The frames that the relay cannot forward on that connection afterwards, which it reports as {@code Failed to forward SUBSCRIBE} and
 * {@code Failed to forward DISCONNECT}. They are only warnings for a session that the broker rejected before. Without that, they can mean that the connection to the
 * broker was lost, which is an error of the server.</li>
 * </ul>
 * Every other error of the two loggers, for example a lost connection to the message broker, stays an error. A warning is only logged if no other filter vetoes it and
 * the logger logs warnings.
 */
public class StompClientErrorLogFilter extends TurboFilter {

    private static final String SUB_PROTOCOL_HANDLER = "org.springframework.web.socket.messaging.StompSubProtocolHandler";

    private static final String BROKER_RELAY = "org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler";

    private static final String FAILED_TO_PARSE_FRAME = "Failed to parse TextMessage payload=";

    private static final String NO_DECODER_FOR_SESSION = "No decoder for session id";

    /** The start of the messages that the broker relay logs when the broker rejected a frame of a client that broke the protocol. */
    private static final List<String> BROKER_REJECTIONS = List.of("Received ERROR {message=[There already is a subscription for: ",
            "Received ERROR {message=[Cannot unsubscribe as no subscription exists for id: ");

    /** The start of the messages that the broker relay logs when it cannot forward a frame. */
    private static final List<String> FAILED_FORWARDS = List.of("Failed to forward SUBSCRIBE ", "Failed to forward DISCONNECT ");

    private static final Pattern SESSION = Pattern.compile("session=([0-9a-fA-F-]{36})");

    /** How long a session stays remembered, which is far more than the frames that follow a rejection need. */
    private static final long SESSION_MEMORY_NANOS = Duration.ofMinutes(10).toNanos();

    private static final int MAX_REMEMBERED_SESSIONS = 10_000;

    /** The sessions whose frame the broker rejected, with the time of the rejection in nanoseconds. */
    private final Map<String, Long> rejectedSessions = new ConcurrentHashMap<>();

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        if (!Level.ERROR.equals(level) || format == null || !isCausedByClient(logger.getName(), format, throwable)) {
            return FilterReply.NEUTRAL;
        }
        // The warning must pass the filters that the error would have passed, with its own level. A filter that vetoes it keeps the veto.
        FilterReply reply = decisionOfOtherFilters(marker, logger, format, params, throwable);
        if (reply == FilterReply.DENY) {
            return FilterReply.DENY;
        }
        if (reply == FilterReply.NEUTRAL && !Level.WARN.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }
        // A turbo filter cannot change the level of an event, so the event is passed on as a warning, and the error is denied. Handing the event to the appenders directly
        // does not run the filters again.
        var warning = new LoggingEvent(StompClientErrorLogFilter.class.getName(), logger, Level.WARN, format, throwable, params);
        if (marker != null) {
            warning.addMarker(marker);
        }
        logger.callAppenders(warning);
        return FilterReply.DENY;
    }

    /** The decision of the first other filter that does not stay neutral about the warning, as the chain of the context decides. */
    private FilterReply decisionOfOtherFilters(Marker marker, Logger logger, String format, Object[] params, Throwable throwable) {
        for (TurboFilter filter : logger.getLoggerContext().getTurboFilterList()) {
            if (filter == this) {
                continue;
            }
            FilterReply reply = filter.decide(marker, logger, Level.WARN, format, params, throwable);
            if (reply != FilterReply.NEUTRAL) {
                return reply;
            }
        }
        return FilterReply.NEUTRAL;
    }

    private boolean isCausedByClient(String loggerName, String message, Throwable throwable) {
        return switch (loggerName) {
            case SUB_PROTOCOL_HANDLER -> message.startsWith(FAILED_TO_PARSE_FRAME) && throwable instanceof IllegalStateException && throwable.getMessage() != null
                    && throwable.getMessage().startsWith(NO_DECODER_FOR_SESSION);
            case BROKER_RELAY -> isBrokerRejection(message) || isForwardFailureOfRejectedSession(message);
            default -> false;
        };
    }

    private boolean isBrokerRejection(String message) {
        if (BROKER_REJECTIONS.stream().noneMatch(message::startsWith)) {
            return false;
        }
        sessionOf(message).ifPresent(this::remember);
        return true;
    }

    private boolean isForwardFailureOfRejectedSession(String message) {
        if (FAILED_FORWARDS.stream().noneMatch(message::startsWith)) {
            return false;
        }
        return sessionOf(message).map(session -> {
            Long rejectedAt = rejectedSessions.get(session);
            return rejectedAt != null && System.nanoTime() - rejectedAt < SESSION_MEMORY_NANOS;
        }).orElse(false);
    }

    private static Optional<String> sessionOf(String message) {
        Matcher matcher = SESSION.matcher(message);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private void remember(String session) {
        long now = System.nanoTime();
        if (rejectedSessions.size() >= MAX_REMEMBERED_SESSIONS) {
            rejectedSessions.values().removeIf(rejectedAt -> now - rejectedAt >= SESSION_MEMORY_NANOS);
            if (rejectedSessions.size() >= MAX_REMEMBERED_SESSIONS) {
                rejectedSessions.clear();
            }
        }
        rejectedSessions.put(session, now);
    }
}
