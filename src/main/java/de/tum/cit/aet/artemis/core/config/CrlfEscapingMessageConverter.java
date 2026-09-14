package de.tum.cit.aet.artemis.core.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * A Logback converter that renders the log message with every line break escaped, so that no value interpolated into a
 * message can forge a log record.
 *
 * <p>
 * Artemis logs a large amount of user-supplied data: logins, credential ids, exercise titles, repository and video
 * URLs, LTI parameters. A carriage return or line feed in any of those ends the current record as far as a log
 * collector is concerned, and everything after it is read as a record of its own — with whatever timestamp, level and
 * logger name the attacker chose to write. That is enough to hide an action inside fabricated noise, or to pin a
 * fabricated action on somebody else. SLF4J's {@code {}} placeholders do not help: they control where a value is
 * substituted, not what it may contain.
 *
 * <p>
 * Escaping here rather than at each call site is deliberate. The alternative is a sanitizing helper that every one of
 * the several hundred log statements has to remember to use, which fails the first time somebody forgets. This
 * converter cannot be bypassed by a new log statement, because it replaces the conversion word the pattern itself is
 * built from.
 *
 * <p>
 * Only the message is escaped. The throwable is rendered by a separate conversion word ({@code %wEx}), so stack traces
 * keep their line structure and stay readable.
 *
 * <p>
 * Registered in {@code logback-spring.xml} for the conversion words {@code m}, {@code msg} and {@code message}, which
 * overrides Logback's built-in message converter for every pattern, including the console and file patterns Spring
 * Boot defines.
 */
public class CrlfEscapingMessageConverter extends MessageConverter {

    /**
     * Renders the formatted log message with carriage returns and line feeds replaced by their two-character escapes
     * ({@code \r} and {@code \n}), leaving the rest of the message untouched.
     *
     * @param event the logging event whose message should be rendered.
     * @return the formatted message, guaranteed to occupy a single line.
     */
    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || (message.indexOf('\n') < 0 && message.indexOf('\r') < 0)) {
            // by far the common case: return the original instance rather than allocating a copy
            return message;
        }
        return message.replace("\r", "\\r").replace("\n", "\\n");
    }
}
