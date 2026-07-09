package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of reverting a Hyperion adaptation.
 *
 * @param fullyReverted        whether every captured repository was reset successfully
 * @param revertedRepositories repositories that were reset before the response was returned
 */
@Schema(description = "Result of reverting the last Hyperion adaptation")
public record ExerciseAdaptationRevertResultDTO(@Schema(description = "Whether every captured repository was reverted") boolean fullyReverted,
        @Schema(description = "Repositories that were reset") List<String> revertedRepositories) {
}
