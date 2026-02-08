package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for checklist action responses containing the modified problem statement
 * and optionally the re-analyzed checklist.
 *
 * @param updatedProblemStatement the AI-modified problem statement markdown
 * @param applied                 whether the action was successfully applied
 * @param summary                 a short summary of what was changed
 * @param updatedAnalysis         the re-analyzed checklist for the updated problem statement (null if action failed)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response from applying a checklist action to the problem statement")
public record ChecklistActionResponseDTO(@Schema(description = "The updated problem statement markdown") String updatedProblemStatement,
        @Schema(description = "Whether the action was successfully applied") boolean applied, @Schema(description = "Short summary of what was changed") String summary,
        @Schema(description = "Re-analyzed checklist for the updated problem statement") ChecklistAnalysisResponseDTO updatedAnalysis) {

    /**
     * Creates a failed response when the action could not be applied.
     *
     * @param originalText the original problem statement
     * @return a response indicating failure
     */
    public static ChecklistActionResponseDTO failed(String originalText) {
        return new ChecklistActionResponseDTO(originalText, false, "Action could not be applied", null);
    }
}
