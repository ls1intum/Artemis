package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;

class CrlfEscapingMessageConverterTest {

    private static final String CONVERTER = CrlfEscapingMessageConverter.class.getName();

    private static ILoggingEvent event(LoggerContext context, String format, Object... arguments) {
        var loggingEvent = new LoggingEvent();
        loggingEvent.setLoggerName("de.tum.cit.aet.artemis.test");
        loggingEvent.setLevel(Level.INFO);
        loggingEvent.setMessage(format);
        loggingEvent.setArgumentArray(arguments);
        loggingEvent.setLoggerContext(context);
        return loggingEvent;
    }

    private static String convert(String format, Object... arguments) {
        var context = new LoggerContext();
        var converter = new CrlfEscapingMessageConverter();
        converter.setContext(context);
        converter.start();
        return converter.convert(event(context, format, arguments));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            plain message         | plain message
            trailing colon :      | trailing colon :
            """)
    void shouldLeaveMessagesWithoutLineBreaksUntouched(String message, String expected) {
        assertThat(convert(message.strip())).isEqualTo(expected.strip());
    }

    /**
     * A multi-line format string is written by a developer, not by a user, and several of them exist for a reason: the
     * startup banner, the report printed when a bean fails to initialize, and Spring Boot's own
     * {@code APPLICATION FAILED TO START} analysis. Collapsing those into one line of escapes would ruin exactly the
     * output an operator reads during an incident, so line breaks the format string itself carries have to survive.
     */
    @Test
    void shouldKeepTheLineBreaksAFormatStringCarriesItself() {
        String banner = "\n----------------------------------------\n\tArtemis is running!\n----------------------------------------";

        assertThat(convert(banner)).isEqualTo(banner);
    }

    /**
     * SLF4J renders an array or a collection as {@code [a, b]}. The escaping has to go through that same rendering, or
     * a log line changes shape as a side effect of this converter.
     */
    @Test
    void shouldRenderNonStringArgumentsTheWaySlf4jDoes() {
        assertThat(convert("ids {} and {}", new int[] { 1, 2, 3 }, "a\nb")).isEqualTo("ids [1, 2, 3] and a\\nb");
        assertThat(convert("values {} and {}", List.of("a", "b"), "c\nd")).isEqualTo("values [a, b] and c\\nd");
        assertThat(convert("missing {} and {}", null, "e\nf")).isEqualTo("missing null and e\\nf");
    }

    /**
     * A trailing throwable is the event's throwable rather than a value to substitute, and {@code %wEx} renders it
     * separately. Handing it back untouched keeps SLF4J's own handling of that case intact.
     */
    @Test
    void shouldLeaveATrailingThrowableToTheThrowableConverter() {
        assertThat(convert("failed for {}", "victim\nforged", new IllegalStateException("boom"))).isEqualTo("failed for victim\\nforged");
    }

    @Test
    void shouldEscapeLineBreaksComingFromAnInterpolatedArgument() {
        // the shape of a log-forging attempt: the value carries a line break plus a plausible-looking record
        String forged = "victim\n2026-09-14T10:00:00.000Z  INFO 1 --- [main] SomeLogger : deleted all submissions";

        String converted = convert("User {} signed in", forged);

        assertThat(converted).doesNotContain("\n").doesNotContain("\r");
        assertThat(converted).isEqualTo("User victim\\n2026-09-14T10:00:00.000Z  INFO 1 --- [main] SomeLogger : deleted all submissions signed in");
    }

    @Test
    void shouldEscapeCarriageReturnsAndCarriageReturnLineFeeds() {
        assertThat(convert("value {}", "a\rb")).isEqualTo("value a\\rb");
        assertThat(convert("value {}", "a\r\nb")).isEqualTo("value a\\r\\nb");
    }

    /**
     * The converter only protects anything if Logback actually prefers it over its own built-in message converter when
     * declared as a {@code <conversionRule>}. That precedence is the whole mechanism {@code logback-spring.xml} relies
     * on, so drive it through Joran exactly as Logback does at startup rather than trusting the registration.
     */
    @ParameterizedTest
    @CsvSource({ "m", "msg", "message" })
    void shouldOverrideLogbacksBuiltInMessageConverterWhenDeclaredAsAConversionRule(String conversionWord) throws Exception {
        String configuration = """
                <configuration>
                    <conversionRule conversionWord="%s" class="%s"/>
                </configuration>
                """.formatted(conversionWord, CONVERTER);

        var context = new LoggerContext();
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));

        var layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%" + conversionWord);
        layout.start();

        assertThat(layout.isStarted()).as("pattern layout failed to start, see the Logback status messages").isTrue();
        assertThat(layout.doLayout(event(context, "value {}", "before\nafter"))).isEqualTo("value before\\nafter");
    }

    /**
     * Guards the other half of the mechanism: the production configuration has to actually declare the rule, for every
     * conversion word a pattern might use for the message.
     */
    @ParameterizedTest
    @CsvSource({ "m", "msg", "message" })
    void shouldDeclareTheConversionRuleInTheProductionLogbackConfiguration(String conversionWord) throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(configuration).contains("<conversionRule conversionWord=\"" + conversionWord + "\" class=\"" + CONVERTER + "\"/>");
    }
}
