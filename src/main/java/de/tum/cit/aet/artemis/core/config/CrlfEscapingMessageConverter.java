package de.tum.cit.aet.artemis.core.config;

import org.slf4j.helpers.MessageFormatter;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * A Logback converter that escapes the line breaks in every value interpolated into a log message, so that logged data
 * cannot forge a log record.
 *
 * <p>
 * Artemis logs a large amount of user-supplied data: logins, credential ids, exercise titles, repository and video
 * URLs, LTI parameters. A carriage return or line feed in any of those ends the current record as far as a log
 * collector is concerned, and everything after it is read as a record of its own — with whatever timestamp, level and
 * logger name the attacker chose to write. That is enough to hide an action inside fabricated noise, or to pin a
 * fabricated action on somebody else. SLF4J's {@code {}} placeholders do not help on their own: they control where a
 * value is substituted, not what it may contain.
 *
 * <p>
 * Escaping here rather than at each call site is deliberate. The alternative is a sanitizing helper that every one of
 * the several hundred log statements has to remember to use, which fails the first time somebody forgets. This
 * converter cannot be bypassed by a new log statement that follows the SLF4J convention, because it replaces the
 * conversion word the pattern itself is built from.
 *
 * <p>
 * <b>Only the arguments are escaped, not the format string.</b> A format string is written by a developer, and several
 * are deliberately multi-line: the startup banner in {@code ArtemisApp}, the report
 * {@code DeferredEagerBeanInitializer} prints when a bean fails, and Spring Boot's own
 * {@code APPLICATION FAILED TO START} analysis. Escaping the fully formatted message would collapse each of those into
 * a single line of {@code \n} escapes, which is exactly the output an operator needs to read during an incident. The
 * consequence is that a caller which assembles the whole message itself — {@code log.error(message)} rather than
 * {@code log.error("… {}", value)} — is outside what this converter can protect, so keep interpolating through
 * placeholders.
 *
 * <p>
 * What is escaped is line breaks, and nothing else, which is the whole of the problem for a line-oriented log. A
 * structured format has to encode its values instead, and Spring Boot does that itself through
 * {@code logging.structured.format.console} - no converter is involved on that path.
 *
 * <p>
 * The throwable is rendered by a separate conversion word ({@code %wEx}), so stack traces keep their line structure
 * and stay readable.
 *
 * <p>
 * Registered in {@code logback-spring.xml} for the conversion words {@code m}, {@code msg} and {@code message}, which
 * overrides Logback's built-in message converter for every pattern, including the console and file patterns Spring
 * Boot defines.
 */
public class CrlfEscapingMessageConverter extends MessageConverter {

    /**
     * Renders the log message with the carriage returns and line feeds of its interpolated arguments replaced by their
     * two-character escapes ({@code \r} and {@code \n}).
     *
     * @param event the logging event whose message should be rendered.
     * @return the formatted message, in which only line breaks written into the format string itself survive.
     */
    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || (message.indexOf('\n') < 0 && message.indexOf('\r') < 0)) {
            // by far the common case: nothing to escape, so return the instance Logback already built
            return message;
        }
        Object[] arguments = event.getArgumentArray();
        if (arguments == null || arguments.length == 0) {
            // no interpolation happened, so every line break came from the format string
            return message;
        }
        Object[] escapedArguments = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            escapedArguments[i] = escapeArgument(arguments[i]);
        }
        return MessageFormatter.arrayFormat(event.getMessage(), escapedArguments).getMessage();
    }

    /**
     * Renders one argument the way SLF4J would and escapes its line breaks.
     *
     * <p>
     * The rendering is delegated rather than done with {@link String#valueOf}, because SLF4J prints arrays and
     * collections as {@code [a, b]} and survives a {@code toString()} that throws; reimplementing that here would
     * change what a log line looks like. A throwable is handed back untouched, so that SLF4J still recognizes a
     * trailing one as the event's throwable instead of substituting it into a placeholder.
     *
     * @param argument the argument to render
     * @return the rendered argument with its line breaks escaped, or the argument itself if it is a throwable
     */
    private static Object escapeArgument(Object argument) {
        if (argument instanceof Throwable) {
            return argument;
        }
        String rendered = MessageFormatter.arrayFormat("{}", new Object[] { argument }).getMessage();
        if (rendered == null || (rendered.indexOf('\n') < 0 && rendered.indexOf('\r') < 0)) {
            return rendered;
        }
        return rendered.replace("\r", "\\r").replace("\n", "\\n");
    }
}
