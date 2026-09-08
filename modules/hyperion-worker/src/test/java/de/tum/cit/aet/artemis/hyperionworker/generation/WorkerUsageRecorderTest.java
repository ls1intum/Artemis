package de.tum.cit.aet.artemis.hyperionworker.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Kind;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;

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
        assertThat(recorder.budgetExhausted()).isFalse(); // 20 uncached + 8 weighted cache + 20 output
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "second", 1, 0, 0L)));
        assertThat(recorder.budgetExhausted()).isTrue();
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
        recorder.accept(null);
        recorder.markUncertain();
        recorder.accept(ProviderUsageTransport.restore(new ProviderUsageUpdate.Call("model", "known", 1, 0, 0L)));
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(events).filteredOn(event -> event.kind() == Kind.UNCERTAIN).hasSize(1);
        assertThat(recorder.snapshot().modelCalls()).isEqualTo(1);
    }

    @Test
    void failedEventDeliveryCannotClaimCompleteAccounting() {
        var recorder = new WorkerUsageRecorder(100, 1, true, ignored -> {
            throw new IllegalStateException("disconnected");
        });
        assertThatThrownBy(recorder::recordTurn).isInstanceOf(IllegalStateException.class);
        assertThat(recorder.accountingState()).isEqualTo(AccountingState.INCOMPLETE);
        assertThat(recorder.snapshot().agentTurns()).isEqualTo(1);
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
