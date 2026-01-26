package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisResponseDTO(List<LearningGoalItemDTO> inferredLearningGoals, DifficultyAssessmentDTO suggestedDifficulty, List<ConsistencyIssueDTO> qualityIssues) {
}
