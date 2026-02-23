package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for checklist action requests that modify the problem statement based on analysis results.
 *
 * @param actionType               the type of action to perform
 * @param problemStatementMarkdown the current problem statement to modify
 * @param context                  action-specific context (e.g., issue description, target difficulty, competency title)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to apply an AI-powered checklist action to the problem statement")
public record ChecklistActionRequestDTO(@NotNull @Schema(description = "Type of action to apply") ActionType actionType,
        @NotBlank @Size(max = 50000) @Schema(description = "Current problem statement markdown") String problemStatementMarkdown,
        @Size(max = 20) @Schema(description = "Action-specific context parameters") Map<String, @Size(max = 10000) String> context) {

    /**
     * Enum representing the types of checklist actions that can be applied.
     */
    public enum ActionType {
        /** Fix a single quality issue identified in the analysis */
        FIX_QUALITY_ISSUE,
        /** Fix all quality issues at once */
        FIX_ALL_QUALITY_ISSUES
    }
}
