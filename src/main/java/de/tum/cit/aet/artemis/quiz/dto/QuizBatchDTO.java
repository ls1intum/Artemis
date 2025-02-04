package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchDTO(Long id, ZonedDateTime startTime, Boolean started, Boolean ended) {

    public static QuizBatchDTO of(QuizBatch quizBatch) {
        return new QuizBatchDTO(quizBatch.getId(), quizBatch.getStartTime(), quizBatch.isStarted(), quizBatch.isEnded());
    }

}
