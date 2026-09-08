package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Fully resolved immutable settings for one generation run. Request bounds can only narrow these values through {@link #tightenedBy}.
 *
 * @param name                    the admin-defined profile name echoed back to the caller, or {@code ""} when the deployment configures no profiles
 * @param label                   the instructor-facing label, or {@code null} when the deployment configures no profiles
 * @param maxTurns                hard cap on model turns per attempt
 * @param maxJobDuration          wall-clock deadline for the run
 * @param maxTokensPerJob         provider spend guard, and the number of tokens admission reserves
 * @param stagedGeneration        whether the staged specification/tests/statement workflow applies
 * @param stagedContext           {@code CONTINUOUS} or {@code FRESH}; validated at startup
 * @param contextWindowTokens     the model's usable context window
 * @param chatOptions             prebuilt runtime provider options, or {@code null} when the profile changes neither model nor decoding parameters and the configured
 *                                    {@code ChatModel}'s own options apply unchanged
 * @param engineDefaults          whether every engine-affecting value equals the deployment default, so the run can reuse the shared singletons rather than deriving its own
 * @param providerOptionsOverride whether {@link #chatOptions()} was authored by this profile rather than inherited, which the checkpoint provider contract must reflect
 */
public record HyperionGenerationSettings(String name, @Nullable String label, int maxTurns, Duration maxJobDuration, long maxTokensPerJob, boolean stagedGeneration,
        String stagedContext, int contextWindowTokens, @Nullable OpenAiChatOptions chatOptions, boolean engineDefaults, boolean providerOptionsOverride) {

    public HyperionGenerationSettings {
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("Hyperion effort profile '" + name + "': max-turns must be positive");
        }
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("Hyperion effort profile '" + name + "': max-job-duration must be positive");
        }
        if (maxTokensPerJob <= 0) {
            throw new IllegalArgumentException("Hyperion effort profile '" + name + "': max-tokens-per-job must be positive");
        }
        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("Hyperion effort profile '" + name + "': context-window-tokens must be positive");
        }
    }

    /**
     * Narrows the profile with caller-provided upper bounds.
     *
     * @param requestedMaxTokens      the caller's token bound, or {@code null} to keep the profile's
     * @param requestedMaxJobDuration the caller's wall-clock bound, or {@code null} to keep the profile's
     * @return the narrowed settings, or {@code this} when nothing was narrowed
     */
    public HyperionGenerationSettings tightenedBy(@Nullable Long requestedMaxTokens, @Nullable Duration requestedMaxJobDuration) {
        long tokens = requestedMaxTokens == null ? maxTokensPerJob : Math.min(maxTokensPerJob, requestedMaxTokens);
        Duration duration = requestedMaxJobDuration == null || requestedMaxJobDuration.compareTo(maxJobDuration) >= 0 ? maxJobDuration : requestedMaxJobDuration;
        if (tokens == maxTokensPerJob && duration.equals(maxJobDuration)) {
            return this;
        }
        // Only the wall-clock bound reaches the engine (it sizes the staged authoring budget); a narrower token bound is enforced by the run's usage sink, which the shared
        // singletons already read per run.
        boolean stillEngineDefaults = engineDefaults && duration.equals(maxJobDuration);
        return new HyperionGenerationSettings(name, label, maxTurns, duration, tokens, stagedGeneration, stagedContext, contextWindowTokens, chatOptions, stillEngineDefaults,
                providerOptionsOverride);
    }
}
