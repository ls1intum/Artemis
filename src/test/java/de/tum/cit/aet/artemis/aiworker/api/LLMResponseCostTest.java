package de.tum.cit.aet.artemis.aiworker.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.advisor.UsageAccumulator;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.UsageCalculator;

class LLMResponseCostTest {

    private static Double reportedUsd(org.springframework.ai.chat.metadata.Usage usage) {
        return LLMResponseCost.reportedUsd(usage.getNativeUsage(), usage.getPromptTokens(), usage.getCompletionTokens());
    }

    @Test
    void readsResponsesUsdIncludingZero() {
        for (double cost : new double[] { 0, 0.0000123 }) {
            var nativeUsage = responsesUsage(cost, "USD");
            assertThat(reportedUsd(new DefaultUsage(100, 20, 120, nativeUsage))).isEqualTo(cost);
        }
    }

    @ParameterizedTest
    @CsvSource({ "-1,USD", "NaN,USD", "Infinity,USD", "1,EUR", "1,usd", "1,''" })
    void rejectsInvalidAmountsOrCurrencies(double amount, String currency) {
        assertThat(reportedUsd(new DefaultUsage(100, 20, 120, responsesUsage(amount, currency)))).isNull();
    }

    @Test
    void neverTreatsTheLastCallsCostAsAnAggregatedAmount() {
        var last = new DefaultUsage(100, 20, 120, responsesUsage(0.5, "USD"));
        var previous = new ChatResponse(List.of(), ChatResponseMetadata.builder().usage(last).build());
        var accumulated = UsageCalculator.getCumulativeUsage(last, previous);

        assertThat(accumulated.getPromptTokens()).isEqualTo(200);
        assertThat(reportedUsd(accumulated)).isNull();
        assertThat(reportedUsd(new DefaultUsage(200, 20, 220, responsesUsage(0.5, "USD")))).isNull();
    }

    @Test
    void toolCallingAdvisorAccumulatorAddsEveryRoundWithoutKeepingTheLastCost() {
        var accumulator = new UsageAccumulator();
        var response = new ChatResponse(List.of(), ChatResponseMetadata.builder().usage(new DefaultUsage(100, 20, 120, responsesUsage(0.5, "USD"))).build());
        accumulator.addRoundResponse(response);
        accumulator.addRoundResponse(response);

        var usage = accumulator.accumulatedResponse().getMetadata().getUsage();
        assertThat(usage.getPromptTokens()).isEqualTo(200);
        assertThat(usage.getCompletionTokens()).isEqualTo(40);
        assertThat(reportedUsd(usage)).isNull();
    }

    @Test
    void readsOnlyMatchingTransportedUsage() {
        var transported = Map.of("prompt_tokens", 100, "completion_tokens", 20, "cost", 0.125, "cost_currency", "USD");
        assertThat(reportedUsd(new DefaultUsage(100, 20, 120, transported))).isEqualTo(0.125);
        assertThat(reportedUsd(new DefaultUsage(100, 30, 130, transported))).isNull();
        assertThat(reportedUsd(new DefaultUsage(100, 20))).isNull();
    }

    private static Map<String, Object> responsesUsage(double cost, String currency) {
        return Map.of("prompt_tokens", 100, "completion_tokens", 20, "cost", cost, "cost_currency", currency);
    }
}
