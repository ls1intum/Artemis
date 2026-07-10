package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of reverting a Hyperion adaptation.
 *
 * @param fullyReverted        whether every captured repository was reset successfully
 * @param revertedRepositories repositories that were reset before the response was returned
 * @param completedAt          server time after the revert completed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Result of reverting the last Hyperion adaptation")
public record ExerciseAdaptationRevertResultDTO(@Schema(description = "Whether every captured repository was reverted") boolean fullyReverted,
        @Schema(description = "Repositories that were reset") List<String> revertedRepositories,
        @Schema(description = "Server time after the revert completed") Instant completedAt) {
}
