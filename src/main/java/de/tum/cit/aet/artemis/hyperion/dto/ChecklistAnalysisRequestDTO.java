package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the request to analyze the checklist.
 *
 * @param problemStatementMarkdown The problem statement to analyze (Markdown format)
 * @param declaredDifficulty       The declared difficulty of the exercise (optional)
 * @param language                 The programming language (optional, e.g., JAVA, PYTHON)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisRequestDTO(@NotBlank @Size(max = 50000) String problemStatementMarkdown,
        @Pattern(regexp = "^(EASY|MEDIUM|HARD)$", message = "declaredDifficulty must be EASY, MEDIUM, or HARD") String declaredDifficulty, @Size(max = 50) String language,
        Long exerciseId) {
}
