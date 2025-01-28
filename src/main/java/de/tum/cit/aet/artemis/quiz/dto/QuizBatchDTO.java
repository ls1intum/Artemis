package de.tum.cit.aet.artemis.quiz.dto;

import java.time.ZonedDateTime;

public record QuizBatchDTO(Long id, ZonedDateTime startTime, Boolean started, Boolean ended) {

    public static QuizBatchDTO of(Long id, ZonedDateTime startTime, Boolean started, Boolean ended) {
        return new QuizBatchDTO(id, startTime, started, ended);
    }
}
