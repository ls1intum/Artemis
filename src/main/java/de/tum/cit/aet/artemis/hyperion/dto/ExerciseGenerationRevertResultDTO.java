package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of reverting a Hyperion generation or adaptation.
 *
 * @param fullyReverted        whether every captured repository was reset successfully
 * @param revertedRepositories repositories that were reset before the response was returned
 * @param completedAt          server time after the revert completed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Result of reverting the last Hyperion generation or adaptation")
public record ExerciseGenerationRevertResultDTO(
        @Schema(description = "Whether every captured repository was reverted", requiredMode = Schema.RequiredMode.REQUIRED) boolean fullyReverted,
        @JsonInclude @Schema(description = "Repositories that were reset", requiredMode = Schema.RequiredMode.REQUIRED) List<String> revertedRepositories,
        @Schema(description = "Server time after the revert completed", requiredMode = Schema.RequiredMode.REQUIRED) Instant completedAt) {
}
