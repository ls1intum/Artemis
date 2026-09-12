package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;

class AgentLoopProviderFailureTest {

    @Test
    void missingProviderResponseRemainsUncertainEvenWhenTheRetrySucceeds() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse completed = response("done");
        when(model.call(any(Prompt.class))).thenReturn(null, completed);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(model, times(2)).call(any(Prompt.class));
        verify(usage).markUncertain();
        verify(usage).accept(completed);
    }

    @Test
    void emptySampleRetriesWithinOneTurnAndAccountsBothResponses() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse empty = response(" ");
        ChatResponse completed = response("done");
        when(model.call(any(Prompt.class))).thenReturn(empty, completed);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        var runner = runner(model);

        var result = runner.run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, null);

        assertThat(result).isEqualTo(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, "done"));
        verify(model, times(2)).call(any(Prompt.class));
        verify(usage).recordTurn();
        verify(usage).accept(empty);
        verify(usage).accept(completed);
        verify(usage, never()).markUncertain();
    }

    @Test
    void repeatedEmptySamplesFailRatherThanPretendCompletion() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response(""));
        List<String> steps = new ArrayList<>();

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, null, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(model, times(2)).call(any(Prompt.class));
        assertThat(steps).contains("The AI service returned no usable response.");
    }

    @Test
    void cancellationBetweenEmptySamplesDoesNotSpendOnAnotherRequest() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response(""));
        AtomicBoolean cancelled = new AtomicBoolean();

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, cancelled::get, null, step -> {
            if (step.contains("retrying")) {
                cancelled.set(true);
            }
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void providerFailureDoesNotMultiplySdkRetriesOrClaimCompleteUsage() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider unavailable"));
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(model).call(any(Prompt.class));
        verify(usage).markUncertain();
        verify(usage, never()).accept(any(ChatResponse.class));
    }

    @Test
    void transientProviderStatusIsRetriedWithoutMarkingUsageUncertain() {
        ChatModel model = mock(ChatModel.class);
        ChatResponse completed = response("done");
        when(model.call(any(Prompt.class))).thenThrow(serverError(503)).thenThrow(serverError(500)).thenReturn(completed);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        List<String> steps = new ArrayList<>();

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, steps::add);

        assertThat(result).isEqualTo(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, "done"));
        verify(model, times(3)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        verify(usage).accept(completed);
        assertThat(steps).contains("The AI service is temporarily unavailable; retrying (1 of 5).", "The AI service is temporarily unavailable; retrying (2 of 5).");
    }

    @Test
    void permanentProviderStatusFailsWithoutRetryAndWithoutMarkingUsageUncertain() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(BadRequestException.builder().headers(Headers.builder().build()).build());
        ProviderUsageSink usage = mock(ProviderUsageSink.class);
        List<String> steps = new ArrayList<>();

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, steps::add);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(model).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
        assertThat(steps).contains("The AI service could not complete the request.");
    }

    @Test
    void persistentTransientFailureStopsAfterTheRetryBudget() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(serverError(502));
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        // The first request plus five retries.
        verify(model, times(6)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();
    }

    @Test
    void cancellationDuringProviderRetryBackoffStopsWithoutAnotherRequest() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(serverError(503));
        AtomicBoolean cancelled = new AtomicBoolean();

        var result = runner(model).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, cancelled::get, null, step -> {
            if (step.contains("retrying")) {
                cancelled.set(true);
            }
        });

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.CANCELLED);
        verify(model).call(any(Prompt.class));
    }

    @Test
    void unsentRequestIsRetriedButAReadTimeoutIsNot() {
        ChatModel unreachable = mock(ChatModel.class);
        ChatResponse completed = response("done");
        when(unreachable.call(any(Prompt.class))).thenThrow(new OpenAIIoException("io", new java.net.ConnectException("refused"))).thenReturn(completed);
        ProviderUsageSink usage = mock(ProviderUsageSink.class);

        var result = runner(unreachable).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, usage, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        verify(unreachable, times(2)).call(any(Prompt.class));
        verify(usage, never()).markUncertain();

        ChatModel stalled = mock(ChatModel.class);
        when(stalled.call(any(Prompt.class))).thenThrow(new OpenAIIoException("io", new java.net.http.HttpTimeoutException("read")));
        ProviderUsageSink stalledUsage = mock(ProviderUsageSink.class);

        var stalledResult = runner(stalled).run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, stalledUsage, null);

        assertThat(stalledResult.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        verify(stalled).call(any(Prompt.class));
        verify(stalledUsage).markUncertain();
    }

    @Test
    void interruptedBackoffFailsAndPreservesInterruptWithoutRetrying() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return response("");
        });
        var runner = runner(model);
        runner.setEmptyResponseRetryTimingForTests(1, 1);
        try {
            var result = runner.run("system", "brief", new AgentLoopRunnerTest.RecordingTools(), 4, () -> false, null, null);
            assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(model).call(any(Prompt.class));
        }
        finally {
            Thread.interrupted();
        }
    }

    private static AgentLoopRunner runner(ChatModel model) {
        var runner = new AgentLoopRunner(List.of(model), 128_000, Duration.ZERO, ProviderFailureCooldown.disabled());
        runner.setEmptyResponseRetryTimingForTests(0, 0);
        runner.setProviderRetryTimingForTests(0, 0);
        return runner;
    }

    private static InternalServerException serverError(int status) {
        return InternalServerException.builder().statusCode(status).headers(Headers.builder().build()).build();
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
