package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchCreationDTO(@NotNull ZonedDateTime startTime) {

    public static QuizBatchCreationDTO of(QuizBatch quizBatch) {
        return new QuizBatchCreationDTO(quizBatch.getStartTime());
    }

    public QuizBatch toDomainObject() {
        QuizBatch quizBatch = new QuizBatch();
        quizBatch.setStartTime(this.startTime);
        return quizBatch;
    }
}
