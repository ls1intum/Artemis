package de.tum.cit.aet.artemis.core.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
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
public class StompErrorLogFilter extends Filter<ILoggingEvent> {

    /**
     * Decides whether a log message should be suppressed or passed through.
     *
     * <p>
     * This method checks if the log message originates from the
     * StompBrokerRelayMessageHandler logger and contains the specific error message
     * about the 60000ms connection TTL timeout. If both conditions are met,
     * the log message is suppressed (denied). All other log messages are allowed.
     *
     * @param event the logging event containing the log message and metadata
     * @return {@code FilterReply.DENY} if the message matches the specific error to suppress,
     *         otherwise {@code FilterReply.NEUTRAL} to allow the message through.
     */
    @Override
    public FilterReply decide(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        String message = event.getFormattedMessage();

        // Check if the logger and message match the specific error to suppress
        if ("org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler".equals(loggerName) && message.contains("Did not receive data from")
                && message.contains("connection TTL. The connection will now be closed.")) {
            return FilterReply.DENY; // Suppress this specific log message
        }

        return FilterReply.NEUTRAL; // Allow other messages
    }
}
