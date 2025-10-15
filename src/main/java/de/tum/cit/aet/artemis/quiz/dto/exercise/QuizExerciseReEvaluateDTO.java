package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.QuizQuestionReEvaluateDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseReEvaluateDTO(@NotBlank String title, @NotNull IncludedInOverallScore includedInOverallScore, @NotNull Boolean randomizeQuestionOrder,
        @NotEmpty List<@Valid QuizQuestionReEvaluateDTO> quizQuestions) {
}
