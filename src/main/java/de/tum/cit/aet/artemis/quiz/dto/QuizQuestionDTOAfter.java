package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionDTOAfter(@JsonUnwrapped QuizQuestionDTOBefore quizQuestionDTOBefore, String explanation) {

    public static QuizQuestionDTOAfter of(QuizQuestionDTOBefore quizQuestionDTOBefore, String explanation) {
        return new QuizQuestionDTOAfter(quizQuestionDTOBefore, explanation);
    }
}
