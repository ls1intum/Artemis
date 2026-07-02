package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for problem statement generation (the "draft a plan to review" step).
 * <p>
 * On success, {@code draftProblemStatement} contains the generated text (never null or empty). The three {@code suggested*} fields are the metadata the same draft call proposes
 * for the instructor to review and edit (a title, the difficulty, and topic categories); they are best-effort and any of them may be {@code null}/empty. Suggestions NEVER drive
 * generation — they are editable pre-fills the instructor confirms or overrides.
 *
 * @param draftProblemStatement the generated problem statement text (non-empty on success, never null)
 * @param suggestedTitle        a concise title the model proposes for the exercise, or {@code null}
 * @param suggestedDifficulty   the difficulty the model assessed for the drafted exercise, or {@code null}
 * @param suggestedCategories   1-3 short topic categories the model proposes (raw, snapped to the course taxonomy on the client), or {@code null}/empty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing the generated problem statement and the metadata the draft proposes for instructor review")
public record ProblemStatementGenerationResponseDTO(@Schema(description = "Draft problem statement text") String draftProblemStatement,
        @Schema(description = "Proposed exercise title") @Nullable String suggestedTitle,
        @Schema(description = "Proposed difficulty (EASY, MEDIUM or HARD)") @Nullable DifficultyLevel suggestedDifficulty,
        @Schema(description = "Proposed topic categories (raw, to be snapped to the course taxonomy on the client)") @Nullable List<String> suggestedCategories) {

    /**
     * Convenience factory for the common case of a draft with no parsed suggestions (backward-compatible callers / fail-open path).
     *
     * @param draftProblemStatement the generated problem statement text
     * @return a response carrying only the statement, all suggestions {@code null}
     */
    public static ProblemStatementGenerationResponseDTO of(String draftProblemStatement) {
        return new ProblemStatementGenerationResponseDTO(draftProblemStatement, null, null, null);
    }
}
