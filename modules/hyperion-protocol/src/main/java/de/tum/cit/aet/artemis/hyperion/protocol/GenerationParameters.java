package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Core-resolved run parameters. Provider endpoints and credentials are deliberately absent. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationParameters(String effortProfile, int maxTurns, long maxTokens, Duration maxDuration, int contextWindowTokens, @Nullable String model,
        @Nullable String reasoningEffort, @Nullable Double temperature, @Nullable Double topP, @Nullable Integer maxCompletionTokens, boolean stagedGeneration,
        String stagedContext) {

    public GenerationParameters {
        if (effortProfile == null || effortProfile.length() > 64 || maxTurns <= 0 || maxTokens <= 0 || maxDuration == null || !maxDuration.isPositive() || contextWindowTokens <= 0
                || !java.util.Set.of("CONTINUOUS", "FRESH").contains(stagedContext)) {
            throw new IllegalArgumentException("Invalid resolved generation parameters");
        }
    }
}
