package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionAfterDTO(@JsonUnwrapped QuizQuestionDuringDTO quizQuestionDuringDTO, String explanation) {

    public static QuizQuestionAfterDTO of(QuizQuestion quizQuestion) {
        return new QuizQuestionAfterDTO(QuizQuestionDuringDTO.of(quizQuestion), quizQuestion.getExplanation());
    }

}
