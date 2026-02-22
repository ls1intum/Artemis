package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.DifficultyDelta;
import de.tum.cit.aet.artemis.hyperion.domain.SuggestedDifficulty;

/**
 * DTO for the difficulty assessment.
 *
 * @param suggested       The suggested difficulty level
 * @param confidence      Confidence score (0.0 to 1.0) for the assessment
 * @param reasoning       The reasoning behind the suggestion (2-4 sentences)
 * @param matchesDeclared Whether the suggested difficulty matches the declared
 *                            difficulty
 * @param delta           Comparison result between suggested and declared difficulty
 * @param taskCount       Number of task markers in the exercise (structural metric)
 * @param testCount       Number of test cases in the exercise (structural metric)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DifficultyAssessmentDTO(SuggestedDifficulty suggested, Double confidence, String reasoning, Boolean matchesDeclared, DifficultyDelta delta, Integer taskCount,
        Integer testCount) {

    /** Creates an unknown assessment (when analysis fails). */
    public static DifficultyAssessmentDTO unknown(String reason) {
        return new DifficultyAssessmentDTO(SuggestedDifficulty.UNKNOWN, 0.0, reason, null, DifficultyDelta.UNKNOWN, null, null);
    }
}
