package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationLiveUsageDTO;

class GenerationLiveUsageTest {

    private static final double CACHED_INPUT_TOKEN_WEIGHT = 0.5d;

    private static ChatResponse response(int promptTokens, int completionTokens, Long cachedInputTokens) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getCacheReadInputTokens()).thenReturn(cachedInputTokens);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        ChatResponse response = mock(ChatResponse.class);
        when(response.getMetadata()).thenReturn(metadata);
        return response;
    }

    private static LLMRequest pricedRequest() {
        return new LLMRequest("model", 1000, 1f, 100, 2f, "pipeline", "provider-id", 800L, 0.1f, true);
    }

    /** An unpriced model reaches the accumulator exactly like this: zero prices, and the recorded request saying the estimate is not complete. */
    private static LLMRequest unpricedRequest() {
        return new LLMRequest("mystery-model", 1000, 0f, 100, 0f, "pipeline", "provider-id", 800L, 0f, false);
    }

    @Test
    void billableTokensDiscountWhatTheProviderServedFromItsCache() {
        // The reported figure has to be the one the budget guard charges, or the instructor watches a bar that does not match the budget that stops the run.
        GenerationLiveUsage liveUsage = new GenerationLiveUsage(10_000, CACHED_INPUT_TOKEN_WEIGHT);

        long total = liveUsage.addBillableTokens(response(1000, 100, 800L));

        assertThat(total).isEqualTo(700);
        ExerciseGenerationLiveUsageDTO snapshot = liveUsage.snapshot();
        assertThat(snapshot.billableTokens()).isEqualTo(700);
        assertThat(snapshot.tokenBudget()).isEqualTo(10_000);
    }

    @Test
    void reportedTokensStayExactWhileTheBudgetChargeIsWeighted() {
        GenerationLiveUsage liveUsage = new GenerationLiveUsage(10_000, CACHED_INPUT_TOKEN_WEIGHT);

        liveUsage.recordAccountedRequest(pricedRequest());
        liveUsage.addBillableTokens(response(1000, 100, 800L));

        ExerciseGenerationLiveUsageDTO snapshot = liveUsage.snapshot();
        assertThat(snapshot.inputTokens()).isEqualTo(1000);
        assertThat(snapshot.outputTokens()).isEqualTo(100);
        assertThat(snapshot.cachedInputTokens()).isEqualTo(800);
        assertThat(snapshot.modelCalls()).isEqualTo(1);
        assertThat(snapshot.billableTokens()).isEqualTo(700);
    }

    @Test
    void aPricedRunReportsItsCostFromTheRecordedPrices() {
        GenerationLiveUsage liveUsage = new GenerationLiveUsage(10_000, CACHED_INPUT_TOKEN_WEIGHT);

        liveUsage.recordAccountedRequest(pricedRequest());

        ExerciseGenerationLiveUsageDTO snapshot = liveUsage.snapshot();
        // 200 uncached input at 1, 800 cache reads at 0.1, 100 output at 2, per million.
        assertThat(snapshot.estimatedCostEur()).isEqualTo(480 / 1_000_000.0);
        assertThat(snapshot.estimatedCostComplete()).isTrue();
    }

    @Test
    void anUnpricedModelReportsNoCostInsteadOfClaimingTheRunWasFree() {
        GenerationLiveUsage liveUsage = new GenerationLiveUsage(10_000, CACHED_INPUT_TOKEN_WEIGHT);

        liveUsage.recordAccountedRequest(unpricedRequest());

        ExerciseGenerationLiveUsageDTO snapshot = liveUsage.snapshot();
        assertThat(snapshot.estimatedCostEur()).isNull();
        assertThat(snapshot.estimatedCostComplete()).isFalse();
        assertThat(snapshot.inputTokens()).isEqualTo(1000);
    }

    @Test
    void oneUnpricedCallLeavesTheEstimateIncompleteForTheRestOfTheRun() {
        GenerationLiveUsage liveUsage = new GenerationLiveUsage(10_000, CACHED_INPUT_TOKEN_WEIGHT);

        liveUsage.recordAccountedRequest(unpricedRequest());
        liveUsage.recordAccountedRequest(pricedRequest());

        assertThat(liveUsage.snapshot().estimatedCostEur()).isNull();
        assertThat(liveUsage.snapshot().estimatedCostComplete()).isFalse();
        assertThat(liveUsage.snapshot().modelCalls()).isEqualTo(2);
    }

    @Test
    void aRunThatHasNotCalledTheProviderYetReportsItsBudgetAndNothingSpent() {
        ExerciseGenerationLiveUsageDTO snapshot = new GenerationLiveUsage(250_000, CACHED_INPUT_TOKEN_WEIGHT).snapshot();

        assertThat(snapshot.modelCalls()).isZero();
        assertThat(snapshot.billableTokens()).isZero();
        assertThat(snapshot.tokenBudget()).isEqualTo(250_000);
        assertThat(snapshot.estimatedCostEur()).isZero();
        assertThat(snapshot.estimatedCostComplete()).isTrue();
    }
}
