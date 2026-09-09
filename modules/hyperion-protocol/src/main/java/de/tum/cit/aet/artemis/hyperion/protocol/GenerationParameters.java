package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Core-resolved run parameters. The empty effort profile selects deployment defaults and must survive a wire round trip. Provider credentials are deliberately absent. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationParameters(String effortProfile, int maxTurns, long maxTokens, Duration maxDuration, int contextWindowTokens, @Nullable String model,
        @Nullable String reasoningEffort, @Nullable Double temperature, @Nullable Double topP, @Nullable Integer maxCompletionTokens, boolean stagedGeneration,
        String stagedContext) {

    public GenerationParameters {
        // NON_EMPTY omits the empty profile; an absent wire value selects deployment defaults.
        effortProfile = effortProfile == null ? "" : effortProfile;
        if (effortProfile.length() > 64 || maxTurns <= 0 || maxTokens <= 0 || maxDuration == null || !maxDuration.isPositive() || contextWindowTokens <= 0
                || !java.util.Set.of("CONTINUOUS", "FRESH").contains(stagedContext)) {
            throw new IllegalArgumentException("Invalid resolved generation parameters");
        }
    }
}
