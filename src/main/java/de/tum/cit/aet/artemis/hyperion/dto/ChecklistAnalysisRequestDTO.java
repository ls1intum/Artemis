package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the request to analyze the checklist.
 *
 *
 * @param problemStatement      The problem statement to analyze
 * @param existingDifficulty    The existing difficulty of the exercise
 * @param existingLearningGoals The existing learning goals of the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisRequestDTO(@NotNull String problemStatement, String existingDifficulty, List<String> existingLearningGoals) {
}
