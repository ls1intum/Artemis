package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionTrainingDTO(@JsonUnwrapped QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated) {

    public static QuizQuestionTrainingDTO of(QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated) {
        return new QuizQuestionTrainingDTO(quizQuestionWithSolutionDTO, isRated);
    }

    public long getId() {
        return quizQuestionWithSolutionDTO().quizQuestionBaseDTO().id();
    }
}
