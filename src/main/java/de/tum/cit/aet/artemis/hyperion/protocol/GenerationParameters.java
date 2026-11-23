package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Duration;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Core-resolved run parameters. The empty effort profile selects deployment defaults and must survive a wire round trip. The model is named by its LLM
 * preset, which the worker resolves in its own {@code artemis.llm} configuration; provider credentials are deliberately absent. The optional repair preset names the
 * model that may take over one stalled repair block, resolved the same way.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationParameters(String effortProfile, int maxTurns, long maxTokens, Duration maxDuration, int contextWindowTokens, String llmPreset, boolean stagedGeneration,
        String stagedContext, double cachedInputTokenWeight, @Nullable String repairLlmPreset) {

    public GenerationParameters(String effortProfile, int maxTurns, long maxTokens, Duration maxDuration, int contextWindowTokens, String llmPreset, boolean stagedGeneration,
            String stagedContext, double cachedInputTokenWeight) {
        this(effortProfile, maxTurns, maxTokens, maxDuration, contextWindowTokens, llmPreset, stagedGeneration, stagedContext, cachedInputTokenWeight, null);
    }

    public GenerationParameters {
        // NON_EMPTY omits the empty profile; an absent wire value selects deployment defaults.
        effortProfile = effortProfile == null ? "" : effortProfile;
        if (repairLlmPreset != null && repairLlmPreset.isBlank()) {
            throw new IllegalArgumentException("repair-llm-preset must not be blank");
        }
        if (effortProfile.length() > 64 || maxTurns <= 0 || maxTokens <= 0 || maxDuration == null || !maxDuration.isPositive() || contextWindowTokens <= 0 || llmPreset == null
                || llmPreset.isBlank() || !Double.isFinite(cachedInputTokenWeight) || cachedInputTokenWeight <= 0 || cachedInputTokenWeight > 1
                || !Set.of("CONTINUOUS", "FRESH").contains(stagedContext)) {
            throw new IllegalArgumentException("Invalid resolved generation parameters");
        }
    }
}
