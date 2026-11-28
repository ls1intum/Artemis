package de.tum.cit.aet.artemis.aiworker.api;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reads the USD amount on an individual provider response, never a partial amount for accumulated usage. */
public final class LLMResponseCost {

    private static final Logger log = LoggerFactory.getLogger(LLMResponseCost.class);

    private LLMResponseCost() {
    }

    /**
     * @param nativeUsage  native usage for one provider response; accumulated usage has no native amount
     * @param inputTokens  the response prompt count
     * @param outputTokens the response completion count
     * @return finite, non-negative cost with an explicit USD currency and matching token counts; otherwise null
     */
    @Nullable
    public static Double reportedUsd(@Nullable Object nativeUsage, @Nullable Integer inputTokens, @Nullable Integer outputTokens) {
        try {
            if (nativeUsage instanceof Map<?, ?> transported && transported.get("prompt_tokens") instanceof Number input
                    && transported.get("completion_tokens") instanceof Number output && countsMatch(inputTokens, outputTokens, input.longValue(), output.longValue())
                    && Objects.equals(transported.get("cost_currency"), "USD") && transported.get("cost") instanceof Number amount) {
                return validAmount(amount.doubleValue());
            }
        }
        catch (RuntimeException e) {
            log.warn("Ignoring unusable provider USD cost metadata: {}", e.getClass().getSimpleName());
        }
        return null;
    }

    private static boolean countsMatch(@Nullable Integer inputTokens, @Nullable Integer outputTokens, long input, long output) {
        return input >= 0 && output >= 0 && inputTokens != null && outputTokens != null && input == inputTokens && output == outputTokens;
    }

    @Nullable
    private static Double validAmount(double amount) {
        if (Double.isFinite(amount) && amount >= 0) {
            return amount;
        }
        log.warn("Ignoring invalid provider USD cost metadata");
        return null;
    }
}
