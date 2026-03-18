package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for the request to analyze the checklist.
 *
 * @param problemStatementMarkdown The problem statement to analyze (Markdown format)
 * @param declaredDifficulty       The declared difficulty of the exercise (optional, e.g., EASY, MEDIUM, HARD)
 * @param language                 The programming language (optional, e.g., JAVA, PYTHON)
 * @param exerciseId               The ID of the exercise (positive Long, optional)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to analyze a problem statement for quality, competencies, and difficulty")
public record ChecklistAnalysisRequestDTO(@NotBlank @Size(max = 50000) @Schema(description = "Problem statement in Markdown format") String problemStatementMarkdown,
        @Pattern(regexp = "EASY|MEDIUM|HARD", message = "declaredDifficulty must be EASY, MEDIUM, or HARD") @Size(max = 20) @Schema(description = "Declared difficulty of the exercise") String declaredDifficulty,
        @Size(max = 50) @Schema(description = "Programming language, e.g. JAVA, PYTHON") String language, @Positive @Schema(description = "ID of the exercise") Long exerciseId) {
}
