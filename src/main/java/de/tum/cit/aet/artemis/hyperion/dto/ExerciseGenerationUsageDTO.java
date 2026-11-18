package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aggregated provider usage and reconciliation identifiers recorded for one exercise-generation job.
 *
 * @param modelCalls                 the number of provider responses recorded for this run
 * @param toolCalls                  the number of tool calls the model requested across the run
 * @param agentTurns                 the number of agent loop turns the run started, summed over every attempt including the ones abandoned at a gate; with {@code attempts} it
 *                                       separates a few long attempts from many short ones, which is what {@code artemis.hyperion.agent.max-turns} is tuned against
 * @param attempts                   the number of authoring attempts the run started
 * @param inputTokens                total prompt tokens billed across the run
 * @param outputTokens               total completion tokens billed across the run
 * @param cachedInputTokens          the share of {@code inputTokens} the provider reported as cache hits
 * @param cachedInputTokensComplete  whether every recorded response reported its cached-token split
 * @param estimatedCostEur           the estimated cost in EUR from the configured per-model prices
 * @param estimatedCostEurComplete   whether a price was configured for every recorded response
 * @param models                     the distinct model identifiers used, in first-use order
 * @param providerRequestIds         the distinct provider request identifiers, for reconciliation against a provider invoice
 * @param providerRequestIdsComplete whether every recorded response carried a provider request identifier
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGenerationUsageDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long modelCalls, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long toolCalls,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long agentTurns, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long attempts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputTokens, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long outputTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cachedInputTokens, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cachedInputTokensComplete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double estimatedCostEur, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean estimatedCostEurComplete,
        @JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> models,
        @JsonInclude @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> providerRequestIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean providerRequestIdsComplete) {
}
