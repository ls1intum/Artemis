package de.tum.cit.aet.artemis.quiz.dto.exercise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseReEvaluateDTO(@NotBlank String title, @NotNull IncludedInOverallScore includedInOverallScore, @NotNull Boolean randomizeQuestionOrder) {
}
