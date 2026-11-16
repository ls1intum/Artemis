package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.openai.core.http.Headers;
import com.openai.errors.UnexpectedStatusCodeException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.metis.config.MetisConfiguration;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

/**
 * A failed call to the model endpoint must not carry the prompt into the log: the message of the failure can quote it, and with it the student's code or
 * answers, for instance when the endpoint names the input it refused.
 */
@Isolated("Captures JVM-wide logger state; concurrent Spring bootstrap temporarily suppresses logging.")
class MetisLlmServiceLoggingTest {

    private static final String QUOTED_CODE = "int secretOfTheStudent = 42;";

    private final Logger logger = (Logger) LoggerFactory.getLogger(MetisLlmService.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final ChatModel batchModel = mock(ChatModel.class);

    private MetisLlmService llmService;

    private Level originalLevel;

    @BeforeEach
    void setUp() {
        when(batchModel.getOptions()).thenReturn(ChatOptions.builder().build());
        ChatModel interactiveModel = mock(ChatModel.class);
        when(interactiveModel.getOptions()).thenReturn(ChatOptions.builder().build());
        var clients = new MetisConfiguration.MetisChatClients(ChatClient.create(batchModel), ChatClient.create(interactiveModel), "configured-model");
        llmService = new MetisLlmService(clients, MetisTestProperties.defaults(), mock(LLMTokenUsageService.class));

        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        String testThread = Thread.currentThread().getName();
        appender.addFilter(new Filter<>() {

            @Override
            public FilterReply decide(ILoggingEvent event) {
                return event.getThreadName().equals(testThread) ? FilterReply.ACCEPT : FilterReply.DENY;
            }
        });
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    void shouldKeepTheMessagesOfAFailedCallOutOfItsExceptionAndItsLogLine() {
        UnexpectedStatusCodeException refused = UnexpectedStatusCodeException.builder().statusCode(422).headers(Headers.builder().build()).build();
        when(batchModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("The endpoint refused the input: " + QUOTED_CODE, refused));

        Throwable failure = catchThrowable(() -> llmService.complete(Pipeline.QUESTION_GENERATION, Mode.BATCH, "system", "user", new MetisLlmService.Attribution(1L, 2L, 3L)));

        // The status code behind the wrapper still decides the kind; the exception keeps the types and the status code, but neither the message nor the cause
        assertThat(failure).isInstanceOfSatisfying(MetisLlmException.class, exception -> assertThat(exception.getKind()).isEqualTo(MetisLlmException.Kind.REJECTED)).hasNoCause()
                .hasMessageContaining("IllegalStateException, caused by UnexpectedStatusCodeException (status 422)").hasMessageNotContaining(QUOTED_CODE);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("failed as REJECTED", "(status 422)").doesNotContain(QUOTED_CODE);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }
}
