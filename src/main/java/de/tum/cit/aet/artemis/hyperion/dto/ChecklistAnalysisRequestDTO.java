package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the request to analyze the checklist.
 *
 * @param problemStatementMarkdown The problem statement to analyze (Markdown
 *                                     format)
 * @param declaredDifficulty       The declared difficulty of the exercise
 *                                     (optional)
 * @param language                 The programming language (optional, e.g.,
 *                                     JAVA, PYTHON)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisRequestDTO(@NotNull String problemStatementMarkdown, String declaredDifficulty, String language, Long exerciseId) {
}
