package de.tum.cit.aet.artemis.hyperion.dto;

import java.io.Serial;
import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one generation run has spent up to the moment an event was produced, so an instructor can watch the cost of a run that is still going.
 * <p>
 * It rides the progress events the transcript already carries rather than a second channel or a poll: the run streams an event per agent turn anyway, and the alternative — a
 * client polling the status endpoint for a ticking counter — would read the run's distributed usage map once per tick per viewer. Only the newest snapshot is of any interest, so
 * it is attached to events the replay may evict.
 * <p>
 * {@code billableTokens} is the figure the run's own budget guard charges, which is not {@code inputTokens + outputTokens}: input the provider served from its prompt cache counts
 * at the configured weight, because that is what it is priced at. Reporting the raw sum against {@code tokenBudget} would show a run at five times its true share of the budget.
 * <p>
 * The cost is reported only when a price is configured for every model the run has used. An unpriced model leaves {@code estimatedCostEur} absent so the client says "not priced"
 * rather than claiming the run was free.
 * <p>
 * {@link Serializable} because it is carried inside {@link ExerciseGenerationEventDTO}, which is retained in a distributed Hazelcast map for reconnect/replay.
 *
 * @param inputTokens           prompt tokens the run has been billed so far
 * @param outputTokens          completion tokens the run has been billed so far
 * @param cachedInputTokens     the share of {@code inputTokens} the provider reported as cache hits
 * @param billableTokens        tokens charged against {@code tokenBudget}, with cached input discounted at the configured weight
 * @param tokenBudget           the ceiling this run was admitted against, so the client can render a proportion without knowing the server configuration; zero or less when the
 *                                  deployment configures no ceiling
 * @param modelCalls            provider responses accounted for so far
 * @param estimatedCostEur      the cost in EUR from the configured per-model prices, or null when {@code estimatedCostComplete} is false
 * @param estimatedCostComplete whether a price was configured for every response counted so far
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "What a generation run has spent so far, streamed while it runs")
public record ExerciseGenerationLiveUsageDTO(@Schema(description = "Prompt tokens billed so far", requiredMode = Schema.RequiredMode.REQUIRED) long inputTokens,
        @Schema(description = "Completion tokens billed so far", requiredMode = Schema.RequiredMode.REQUIRED) long outputTokens,
        @Schema(description = "The share of the prompt tokens the provider served from its cache", requiredMode = Schema.RequiredMode.REQUIRED) long cachedInputTokens,
        @Schema(description = "Tokens charged against the run's token budget, with cached input discounted", requiredMode = Schema.RequiredMode.REQUIRED) long billableTokens,
        @Schema(description = "The token ceiling this run was admitted against", requiredMode = Schema.RequiredMode.REQUIRED) long tokenBudget,
        @Schema(description = "Provider responses accounted for so far", requiredMode = Schema.RequiredMode.REQUIRED) long modelCalls,
        @Schema(description = "Estimated cost in EUR, absent when any model used has no configured price") @Nullable Double estimatedCostEur,
        @Schema(description = "Whether a price was configured for every response counted so far", requiredMode = Schema.RequiredMode.REQUIRED) boolean estimatedCostComplete)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
