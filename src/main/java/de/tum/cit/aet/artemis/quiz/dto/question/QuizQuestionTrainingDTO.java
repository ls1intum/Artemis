package de.tum.cit.aet.artemis.quiz.dto.question;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionTrainingDTO(@NotNull QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated) {

    public static QuizQuestionTrainingDTO of(QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated) {
        return new QuizQuestionTrainingDTO(quizQuestionWithSolutionDTO, isRated);
    }

    public long getId() {
        return quizQuestionWithSolutionDTO().quizQuestionBaseDTO().id();
    }
}
