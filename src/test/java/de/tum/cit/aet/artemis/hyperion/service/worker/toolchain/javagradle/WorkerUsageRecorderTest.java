package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Kind;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.SubmitVetoAware;

class WorkerUsageRecorderTest {

    @Test
    void streamsProviderEvidenceAndCountsWithoutInventingPrices() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 0.1, true, events::add);
        var call = new ProviderUsageUpdate.Call("model", "request", 100, 20, 80L);

        recorder.recordAttempt();
        recorder.recordTurn();
        recorder.accept(ProviderUsageTransport.restore(call));
        recorder.recordToolCalls(2);

        assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.ATTEMPT, Kind.TURN, Kind.RESPONSE, Kind.TOOL_CALLS);
        assertThat(events.get(2).call()).isEqualTo(call);
        var snapshot = recorder.snapshot();
        assertThat(snapshot.modelCalls()).isEqualTo(1);
        assertThat(snapshot.toolCalls()).isEqualTo(2);
        assertThat(snapshot.agentTurns()).isEqualTo(1);
        assertThat(snapshot.attempts()).isEqualTo(1);
        assertThat(snapshot.inputTokens()).isEqualTo(100);
        assertThat(snapshot.outputTokens()).isEqualTo(20);
        assertThat(snapshot.cachedInputTokens()).isEqualTo(80);
        assertThat(snapshot.cachedInputTokensComplete()).isTrue();
        assertThat(snapshot.models()).containsExactly("model");
        assertThat(snapshot.providerRequestIds()).containsExactly("request");
        assertThat(snapshot.providerRequestIdsComplete()).isTrue();
        assertThat(snapshot.estimatedCostEurComplete()).isFalse();
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.COMPLETE);
    }

    @Test
    void cachedInputBudgetUsesRoundedWeightedTokensAndStopsAtTheExactLimit() {
        var recorder = new WorkerUsageRecorder(49, 0.1, true, ignored -> {
        });
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "first", 100, 20, 80L)));
        assertThat(recorder.shouldStopAuthoring()).isFalse();
        assertThat(recorder.budgetExhausted()).isFalse(); // 20 uncached + 8 weighted cache + 20 output
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "second", 1, 0, 0L)));
        assertThat(recorder.budgetExhausted()).isTrue();
        assertThat(recorder.shouldStopAuthoring()).isTrue();
    }

    @Test
    void missingCacheAndRequestIdStayUnknownAndCannotDiscountTheBudget() {
        var recorder = new WorkerUsageRecorder(100, 0.1, true, ignored -> {
        });
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", null, 100, 0, null)));
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "known", 1, 0, 0L)));
        assertThat(recorder.budgetExhausted()).isTrue();
        assertThat(recorder.snapshot().cachedInputTokensComplete()).isFalse();
        assertThat(recorder.snapshot().providerRequestIdsComplete()).isFalse();
        assertThat(recorder.snapshot().models()).containsExactly("model");
    }

    @Test
    void retriesAndMissingUsageAreAbsorbingUncertainty() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(100, 1, false, events::add);
        assertThat(recorder.shouldStopAuthoring()).isFalse();
        assertThatThrownBy(() -> recorder.accept(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no usage metadata");
        assertThat(recorder.shouldStopAuthoring()).isTrue();
        assertThat(recorder.budgetExhausted()).isFalse();
        recorder.markUncertain();
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "known", 1, 0, 0L)));
        assertThat(recorder.shouldStopAuthoring()).isTrue();
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(events).filteredOn(event -> event.kind() == Kind.UNCERTAIN).hasSize(1);
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(1);
    }

    @Test
    void missingUsageStopsTheRunnerBeforeToolsOrAnotherRequest() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 1, true, events::add);
        ChatModel model = mock(ChatModel.class);
        var call = new AssistantMessage.ToolCall("edit", "function", "write", "{}");
        var response = new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(call)).build())), mock(ChatResponseMetadata.class));
        when(model.call(any(Prompt.class))).thenReturn(response);
        var tools = new RecordingTools();
        var runner = new AgentLoopRunner(List.of(model), 128_000, Duration.ofMinutes(5), ProviderFailureCooldown.disabled());

        var result = runner.run("system", "brief", tools, 4, () -> false, recorder, null);

        assertThat(result.status()).isEqualTo(AgentLoopResult.Status.ERROR);
        assertThat(tools.writes).isZero();
        verify(model, times(1)).call(any(Prompt.class));
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.TURN, Kind.UNCERTAIN);
    }

    @Test
    void missingSummaryUsageStopsBeforeTheNextAuthoringRequest() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 1, true, events::add);
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("summary"))), mock(ChatResponseMetadata.class)));
        var runner = new AgentLoopRunner(List.of(model), 24_000, Duration.ofMinutes(5), ProviderFailureCooldown.disabled());
        List<Message> prior = new ArrayList<>();
        prior.add(new UserMessage("brief"));
        for (int turn = 0; turn < 12; turn++) {
            prior.add(new AssistantMessage("previous turn"));
            prior.add(new UserMessage("x".repeat(24_000)));
        }

        assertThatThrownBy(() -> runner.runTextSession("system", prior, "continue", 4, () -> false, recorder, null)).hasMessage("Provider usage accounting failed")
                .hasRootCauseMessage("Provider response contains no usage metadata");

        verify(model, times(1)).call(any(Prompt.class));
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.UNCERTAIN);
    }

    @Test
    void retryEnabledAccountingStillAllowsAResponseWithValidUsage() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 1, false, events::add);
        ChatModel model = mock(ChatModel.class);
        var metadata = ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "request", 10, 2, 0L)).getMetadata();
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("finished"))), metadata));
        var runner = new AgentLoopRunner(List.of(model), 128_000, Duration.ofMinutes(5), ProviderFailureCooldown.disabled());

        var session = runner.runTextSession("system", null, "brief", 4, () -> false, recorder, null);

        assertThat(session.result().status()).isEqualTo(AgentLoopResult.Status.COMPLETED);
        assertThat(recorder.shouldStopAuthoring()).isFalse();
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(1);
        assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.UNCERTAIN, Kind.TURN, Kind.RESPONSE, Kind.TOOL_CALLS);
        verify(model, times(1)).call(any(Prompt.class));
    }

    static class RecordingTools implements SubmitVetoAware {

        private int writes;

        @Tool(description = "Record an edit")
        public String write() {
            writes++;
            return "written";
        }

        @Override
        public boolean consumeSubmitVeto() {
            return false;
        }

        @Override
        public boolean isSandboxSessionTerminated() {
            return false;
        }
    }

    @Test
    void failedEventDeliveryCannotClaimCompleteAccounting() {
        var recorder = new WorkerUsageRecorder(100, 1, true, ignored -> {
            throw new IllegalStateException("disconnected");
        });
        assertThatThrownBy(recorder::recordTurn).isInstanceOf(IllegalStateException.class);
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(recorder.snapshot().agentTurns()).isEqualTo(1);
        assertThat(recorder.shouldStopAuthoring()).isTrue();
        assertThat(recorder.budgetExhausted()).isFalse();
    }

    @Test
    void indeterminateUsageStopsEvenWhenRetryUncertaintyWasAlreadyReported() {
        var events = new ArrayList<ProviderUsageUpdate>();
        var recorder = new WorkerUsageRecorder(1_000, 1, false, events::add);
        assertThat(recorder.shouldStopAuthoring()).isFalse();

        recorder.markUncertain();

        assertThat(recorder.shouldStopAuthoring()).isTrue();
        assertThat(recorder.budgetExhausted()).isFalse();
        assertThat(events).extracting(ProviderUsageUpdate::kind).containsExactly(Kind.UNCERTAIN);
    }

    @Test
    void negativeToolCountsCannotDecreaseUsage() {
        var recorder = new WorkerUsageRecorder(100, 1, true, ignored -> {
        });
        recorder.recordToolCalls(2);
        assertThatThrownBy(() -> recorder.recordToolCalls(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(recorder.snapshot().toolCalls()).isEqualTo(2);
    }
}
