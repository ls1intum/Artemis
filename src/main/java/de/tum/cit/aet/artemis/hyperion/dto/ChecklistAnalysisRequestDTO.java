package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChecklistAnalysisRequestDTO(@NotNull String problemStatement, String existingDifficulty, List<String> existingLearningGoals) {
}
