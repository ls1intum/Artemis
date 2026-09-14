package de.tum.cit.aet.artemis.core.config.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Renders a log event as one JSON object per line, with every value encoded by Jackson.
 *
 * <p>
 * This replaces a {@link ch.qos.logback.classic.PatternLayout} whose pattern assembled the JSON by hand, as
 * {@code {"message":"%msg"}}. A pattern cannot encode anything: a quote or a backslash in a logged value ended the
 * JSON string, and everything after it was read as further fields — so any value that reaches a log statement could
 * add or overwrite fields in the record. Artemis logs plenty of user-supplied data (logins, exercise titles,
 * repository URLs), which made that reachable rather than theoretical.
 *
 * <p>
 * Encoding the record properly also subsumes the line breaks that {@link de.tum.cit.aet.artemis.core.config.CrlfEscapingMessageConverter}
 * handles for the plain-text patterns: a line feed inside a JSON string is written as {@code \n} and cannot end the
 * record. That converter is not involved here and is not relied upon.
 *
 * <p>
 * The fields are deliberately the same ones, in the same order, as the pattern produced, so that whatever consumes
 * this output keeps working. One thing the pattern never emitted and this does not add either is the throwable: the
 * JSON format has always dropped stack traces, and giving it one is a change for whoever needs it, not for this fix.
 */
public class JsonLoggingLayout extends LayoutBase<ILoggingEvent> {

    /**
     * Matches the {@code %d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}} the replaced pattern used.
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, String> record = new LinkedHashMap<>();
        record.put("timestamp", TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp())));
        record.put("level", event.getLevel().toString());
        record.put("logger", event.getLoggerName());
        record.put("thread", event.getThreadName());
        record.put("message", event.getFormattedMessage());
        return JsonObjectMapper.get().writeValueAsString(record) + System.lineSeparator();
    }
}
