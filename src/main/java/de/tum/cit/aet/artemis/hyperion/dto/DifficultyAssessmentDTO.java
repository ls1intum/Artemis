package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the difficulty assessment.
 *
 * @param suggested       The suggested difficulty level (EASY, MEDIUM, HARD)
 * @param confidence      Confidence score (0.0 to 1.0) for the assessment
 * @param reasoning       The reasoning behind the suggestion (2-4 sentences)
 * @param matchesDeclared Whether the suggested difficulty matches the declared
 *                            difficulty
 * @param delta           Comparison result: LOWER, MATCH, HIGHER, or UNKNOWN
 * @param taskCount       Number of task markers in the exercise (structural metric)
 * @param testCount       Number of test cases in the exercise (structural metric)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DifficultyAssessmentDTO(String suggested, Double confidence, String reasoning, Boolean matchesDeclared, String delta, Integer taskCount, Integer testCount) {

    /**
     * Creates an unknown assessment (when analysis fails).
     */
    public static DifficultyAssessmentDTO unknown(String reason) {
        return new DifficultyAssessmentDTO("UNKNOWN", 0.0, reason, null, "UNKNOWN", null, null);
    }
}
