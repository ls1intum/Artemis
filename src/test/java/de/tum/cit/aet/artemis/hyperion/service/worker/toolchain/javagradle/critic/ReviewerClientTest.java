package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.PromptTemplates;

/** Real reviewer-boundary diagnostics must describe provider failures without attaching sensitive exceptions. */
class ReviewerClientTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ReviewerClient.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level originalLevel;

    @BeforeEach
    void captureReviewerLogs() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreReviewerLogs() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @Test
    void finalServerFailureLogsOnlySafeClassAndStatusAndRethrowsTheSameException() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var serverFailure = InternalServerException.builder().statusCode(500).headers(Headers.builder().build()).build();
        var failure = new IllegalStateException("private-provider-marker prompt/body/header/key content", serverFailure);
        when(model.call(any(Prompt.class))).thenThrow(failure);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        ReviewerClient reviewer = reviewer(model);

        assertThatThrownBy(() -> reviewer.call("review-template", "private-user-prompt", usage, () -> false)).isSameAs(failure);

        verify(model, times(1)).call(any(Prompt.class));
        verify(usage, times(1)).markUncertain();
        verify(usage, never()).accept(any());
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("Reviewer provider call failed", "INDETERMINATE", "IllegalStateException", "InternalServerException HTTP 500")
                    .doesNotContain("private-provider-marker", "private-user-prompt", "private-system-prompt", "prompt/body/header/key content");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getArgumentArray()).doesNotContain(failure, serverFailure).allSatisfy(argument -> assertThat(argument).isNotInstanceOf(Throwable.class));
        });
    }

    @Test
    void uncertainAccountingDeliveryFailureCannotReplayAnIndeterminateProviderRequest() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var serverFailure = InternalServerException.builder().statusCode(500).headers(Headers.builder().build()).build();
        var providerFailure = new IllegalStateException("private-provider-message", serverFailure);
        var deliveryFailure = new IllegalStateException("private-accounting-message", new ConnectException("private-delivery-message"));
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("private-model-response"))));
        when(model.call(any(Prompt.class))).thenThrow(providerFailure).thenReturn(response);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        doThrow(deliveryFailure).when(usage).markUncertain();
        ReviewerClient reviewer = reviewer(model);
        reviewer.setProviderRetryTimingForTests(0, 0);

        Throwable failure = catchThrowable(() -> reviewer.call("review-template", "private-user-prompt", usage, () -> false));

        verify(model, times(1)).call(any(Prompt.class));
        verify(usage, times(1)).markUncertain();
        verify(usage, never()).accept(any());
        assertThat(failure).isNotNull();
        List<Throwable> retainedFailures = new ArrayList<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            retainedFailures.add(current);
            retainedFailures.addAll(Arrays.asList(current.getSuppressed()));
        }
        assertThat(retainedFailures).contains(deliveryFailure, providerFailure);
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("InternalServerException HTTP 500").doesNotContain("private-provider-message", "private-accounting-message",
                    "private-delivery-message", "private-model-response", "private-user-prompt", "private-system-prompt", "configured-model");
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getArgumentArray()).allSatisfy(argument -> assertThat(argument).isNotInstanceOf(Throwable.class));
        });
    }

    @Test
    void successfulInvocationDoesNotLogAFinalFailure() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("review complete"))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        assertThat(reviewer(model).call("review-template", "private-user-prompt", usage, () -> false)).isEqualTo("review complete");

        verify(model, times(1)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        verify(usage).accept(response);
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).isEmpty();
    }

    @Test
    void knownNotSentFailureCanRetrySuccessfullyWithoutAFinalFailureWarning() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var connectionFailure = new IllegalStateException("private-connection-message", new ConnectException("private-connect-message"));
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("review complete"))));
        when(model.call(any(Prompt.class))).thenThrow(connectionFailure).thenReturn(response);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        ReviewerClient reviewer = reviewer(model);
        reviewer.setProviderRetryTimingForTests(0, 0);

        assertThat(reviewer.call("review-template", "private-user-prompt", usage, () -> false)).isEqualTo("review complete");

        verify(model, times(2)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        verify(usage, times(1)).accept(response);
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).isEmpty();
    }

    @Test
    void localCooldownRejectionDoesNotAttemptTheProviderOrInventUsage() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        ProviderFailureCooldown cooldown = mock(ProviderFailureCooldown.class, CALLS_REAL_METHODS);
        when(cooldown.cooldownUntil("configured-model")).thenReturn(Instant.MAX);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        ReviewerClient reviewer = reviewer(model, cooldown);

        assertThatThrownBy(() -> reviewer.call("review-template", "private-user-prompt", usage, () -> false))
                .isInstanceOf(ProviderFailureCooldown.ProviderInCooldownException.class);

        verify(model, never()).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        verify(usage, never()).accept(any());
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("REJECTED", "ProviderInCooldownException").doesNotContain("private-user-prompt", "private-system-prompt");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void failedCooldownReadAfterRetryDoesNotReuseThePreviousAttemptFlag() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("private-connect-message", new ConnectException("private-connect-cause")));
        var cooldownFailure = new IllegalStateException("private-cooldown-store-message");
        ProviderFailureCooldown cooldown = mock(ProviderFailureCooldown.class, CALLS_REAL_METHODS);
        when(cooldown.cooldownUntil("configured-model")).thenReturn(null).thenThrow(cooldownFailure);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        ReviewerClient reviewer = reviewer(model, cooldown);
        reviewer.setProviderRetryTimingForTests(0, 0);

        assertThatThrownBy(() -> reviewer.call("review-template", "private-user-prompt", usage, () -> false)).isSameAs(cooldownFailure);

        verify(model, times(1)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        verify(usage, never()).accept(any());
        assertThat(appender.list).filteredOn(event -> event.getThreadName().equals(Thread.currentThread().getName())).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("INDETERMINATE", "IllegalStateException").doesNotContain("private-cooldown-store-message", "private-connect-message",
                    "private-user-prompt", "private-system-prompt");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private static ReviewerClient reviewer(ChatModel model) {
        return reviewer(model, ProviderFailureCooldown.disabled());
    }

    private static ReviewerClient reviewer(ChatModel model, ProviderFailureCooldown cooldown) {
        PromptTemplates templates = mock(PromptTemplates.class);
        when(templates.render("review-template", Map.of())).thenReturn("private-system-prompt");
        return new ReviewerClient(ChatClient.create(model), templates, "configured-model", Duration.ZERO, cooldown, 200_000, model.getOptions());
    }
}
