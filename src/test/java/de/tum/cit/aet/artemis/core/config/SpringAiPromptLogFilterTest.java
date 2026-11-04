package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class SpringAiPromptLogFilterTest {

    private static final String FILTER = SpringAiPromptLogFilter.class.getName();

    private static final String QUOTED_CODE = "int secretOfTheStudent = 42;";

    /**
     * Configures the filter through Joran, as Logback does at startup, and logs as the chat model of Spring AI does, with the prompt concatenated into the
     * message: that warning is dropped, while the other warnings of the chat model and the same text from another logger are kept.
     */
    @Test
    void shouldDropTheWarningThatQuotesThePromptAndKeepEveryOtherEvent() throws Exception {
        var context = new LoggerContext();
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream("""
                <configuration>
                    <turboFilter class="%s"/>
                </configuration>
                """.formatted(FILTER).getBytes(StandardCharsets.UTF_8)));
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.start();
        Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        Logger chatModel = context.getLogger(SpringAiPromptLogFilter.OPENAI_CHAT_MODEL_LOGGER);

        chatModel.warn("No choices returned for prompt: Prompt{messages=[UserMessage{content='" + QUOTED_CODE + "'}]}");
        chatModel.warn("The topK option is not supported by OpenAI chat models. Ignoring.");
        context.getLogger("de.tum.cit.aet.artemis.test").warn("No choices returned for prompt: " + QUOTED_CODE);

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly("The topK option is not supported by OpenAI chat models. Ignoring.",
                "No choices returned for prompt: " + QUOTED_CODE);
    }

    /**
     * The filter matches the text of the warning, so it has to follow the chat model on the classpath: a version of Spring AI that words the warning
     * differently would write the prompt into the log again.
     */
    @Test
    void shouldMatchTheWarningOfTheChatModelOnTheClasspath() throws Exception {
        String classFile = SpringAiPromptLogFilter.OPENAI_CHAT_MODEL_LOGGER.replace('.', '/') + ".class";
        try (InputStream chatModel = SpringAiPromptLogFilterTest.class.getClassLoader().getResourceAsStream(classFile)) {
            assertThat(chatModel).as(classFile).isNotNull();
            assertThat(new String(chatModel.readAllBytes(), StandardCharsets.ISO_8859_1)).contains(SpringAiPromptLogFilter.NO_CHOICES_WARNING);
        }
    }

    @Test
    void shouldRegisterTheFilterInTheProductionLogbackConfiguration() throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/logback-spring.xml"));

        assertThat(configuration).contains("<turboFilter class=\"" + FILTER + "\" />");
    }
}
