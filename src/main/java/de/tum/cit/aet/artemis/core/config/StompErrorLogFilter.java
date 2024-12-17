package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * A custom Logback filter to suppress specific log messages from the
 * StompBrokerRelayMessageHandler in a Spring Boot application.
 *
 * <p>
 * This filter identifies log messages containing the error:
 * "Did not receive data from ... within the 60000ms connection TTL. The connection will now be closed."
 * and suppresses them while allowing other log messages to pass through.
 *
 * <p>
 * The purpose of this filter is to reduce noise in the logs by eliminating
 * repetitive or irrelevant error messages caused by client disconnections.
 */
public class StompErrorLogFilter extends TurboFilter {

    /**
     * Determines whether a log message should be allowed, denied, or processed normally.
     *
     * <p>
     * This method checks the logger name, log level, and message format to identify
     * and suppress specific error messages. If the message matches the criteria
     * (e.g., the logger is from {@code StompBrokerRelayMessageHandler} and the error
     * contains details about a 60000ms connection TTL timeout), the log is denied.
     * Otherwise, the log message is processed normally.
     *
     * @param marker The marker associated with the log message (can be null).
     * @param logger The logger that created the log message.
     * @param level  The log level (e.g., ERROR, WARN, INFO).
     * @param format The log message format string.
     * @param params Parameters for the format string (if any).
     * @param t      Throwable associated with the log event (if any).
     * @return {@link FilterReply#DENY} if the message matches the suppression criteria,
     *         otherwise {@link FilterReply#NEUTRAL} to process the message normally.
     */
    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {

        // Check if the logger and message match the specific error to suppress
        if ("org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler".equals(logger.getName()) && Level.ERROR.equals(level) && format != null
                && format.contains("Did not receive data from") && format.contains("connection TTL. The connection will now be closed.")) {
            return FilterReply.DENY; // Suppress this specific log message
        }

        return FilterReply.NEUTRAL; // Allow other messages
    }
}
