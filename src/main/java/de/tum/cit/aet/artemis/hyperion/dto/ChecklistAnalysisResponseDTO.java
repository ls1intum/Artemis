package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the response of the checklist analysis.
 *
 *
 * @param inferredLearningGoals The inferred learning goals
 * @param suggestedDifficulty   The suggested difficulty
 * @param qualityIssues         The quality issues
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisResponseDTO(List<LearningGoalItemDTO> inferredLearningGoals, DifficultyAssessmentDTO suggestedDifficulty, List<ConsistencyIssueDTO> qualityIssues) {
}
