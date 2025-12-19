package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchWithPasswordDTO(@JsonUnwrapped QuizBatchDTO quizBatchDTO, String password) {

    public static QuizBatchWithPasswordDTO of(QuizBatch quizBatch) {
        return new QuizBatchWithPasswordDTO(QuizBatchDTO.of(quizBatch), quizBatch.getPassword());
    }
}
