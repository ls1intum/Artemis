package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the difficulty assessment.
 *
 * @param suggested       The suggested difficulty level (EASY, MEDIUM, HARD)
 * @param reasoning       The reasoning behind the suggestion
 * @param matchesDeclared Whether the suggested difficulty matches the declared difficulty
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DifficultyAssessmentDTO(String suggested, // EASY, MEDIUM, HARD
        String reasoning, boolean matchesDeclared) {
}
