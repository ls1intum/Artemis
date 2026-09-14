package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertThat(convert("a\rb")).isEqualTo("a\\rb");
        assertThat(convert("a\r\nb")).isEqualTo("a\\r\\nb");
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
        assertThat(layout.doLayout(event(context, "before\nafter"))).isEqualTo("before\\nafter");
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
