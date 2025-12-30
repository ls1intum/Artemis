package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

/**
 * DTO for short answer solutions in the editor context.
 * Supports both creating new solutions (id is null) and updating existing solutions (id is non-null).
 *
 * @param id     the ID of the solution, null for new solutions
 * @param tempID the temporary ID for matching during creation (can be null for persisted entities, will use id instead)
 * @param text   the solution text
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSolutionFromEditorDTO(Long id, Long tempID, @NotEmpty String text) {

    /**
     * Creates a ShortAnswerSolutionFromEditorDTO from the given ShortAnswerSolution domain object.
     * For persisted entities, uses the id as tempID if tempID is null.
     *
     * @param solution the solution to convert
     * @return the corresponding DTO
     */
    public static ShortAnswerSolutionFromEditorDTO of(ShortAnswerSolution solution) {
        // Use id as tempID fallback for persisted entities
        Long effectiveTempID = solution.getTempID() != null ? solution.getTempID() : solution.getId();
        return new ShortAnswerSolutionFromEditorDTO(solution.getId(), effectiveTempID, solution.getText());
    }

    /**
     * Creates a new ShortAnswerSolution domain object from this DTO.
     *
     * @return a new ShortAnswerSolution domain object
     */
    public ShortAnswerSolution toDomainObject() {
        ShortAnswerSolution solution = new ShortAnswerSolution();
        // Use id as tempID fallback for mapping resolution
        solution.setTempID(tempID != null ? tempID : id);
        solution.setText(text);
        return solution;
    }

    /**
     * Applies the DTO values to an existing ShortAnswerSolution entity.
     *
     * @param solution the existing solution to update
     */
    public void applyTo(ShortAnswerSolution solution) {
        solution.setTempID(tempID != null ? tempID : id);
        solution.setText(text);
    }

    /**
     * Gets the effective ID used for mapping resolution (tempID if available, otherwise id).
     *
     * @return the effective ID for matching
     */
    public Long effectiveId() {
        return tempID != null ? tempID : id;
    }
}
