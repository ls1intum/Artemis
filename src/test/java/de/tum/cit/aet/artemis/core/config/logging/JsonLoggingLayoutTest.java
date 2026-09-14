package de.tum.cit.aet.artemis.core.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class JsonLoggingLayoutTest {

    private static ILoggingEvent event(String format, Object... arguments) {
        var context = new LoggerContext();
        var loggingEvent = new LoggingEvent();
        loggingEvent.setLoggerName("de.tum.cit.aet.artemis.test");
        loggingEvent.setThreadName("main");
        loggingEvent.setLevel(Level.INFO);
        loggingEvent.setMessage(format);
        loggingEvent.setArgumentArray(arguments);
        loggingEvent.setTimeStamp(1_757_000_000_000L);
        loggingEvent.setLoggerContext(context);
        return loggingEvent;
    }

    private static JsonNode layout(String format, Object... arguments) {
        var layout = new JsonLoggingLayout();
        layout.setContext(new LoggerContext());
        layout.start();
        String rendered = layout.doLayout(event(format, arguments));

        assertThat(rendered).as("one record per line").endsWith(System.lineSeparator());
        assertThat(rendered.strip()).as("a record must not be split across lines").doesNotContain("\n").doesNotContain("\r");
        return JsonObjectMapper.get().readTree(rendered);
    }

    @Test
    void shouldRenderTheFieldsTheReplacedPatternProduced() {
        JsonNode record = layout("hello {}", "world");

        assertThat(record.get("level").asString()).isEqualTo("INFO");
        assertThat(record.get("logger").asString()).isEqualTo("de.tum.cit.aet.artemis.test");
        assertThat(record.get("thread").asString()).isEqualTo("main");
        assertThat(record.get("message").asString()).isEqualTo("hello world");
        assertThat(record.get("timestamp").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{4}");
    }

    /**
     * The defect this guards: the replaced layout built the JSON with a pattern, so it could not escape anything. A
     * quote ended the string the value sat in and everything after it was parsed as further fields, which let any
     * logged value overwrite the record - including the ones an attacker chooses, such as a login or an exercise title.
     */
    @ParameterizedTest
    @ValueSource(strings = { "victim\",\"level\":\"ERROR", "victim\\", "victim\"}{\"forged\":\"yes", "victim\nforged", "victim\r\nforged", "tab\there" })
    void shouldKeepAValueInsideItsFieldWhateverItContains(String value) {
        JsonNode record = layout("user {} signed in", value);

        assertThat(record.get("message").asString()).isEqualTo("user " + value + " signed in");
        assertThat(record.get("level").asString()).as("a logged value must not be able to change another field").isEqualTo("INFO");
        assertThat(record.has("forged")).as("a logged value must not be able to add a field").isFalse();
    }
}
