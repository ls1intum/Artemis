package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The only quiz-batch state read by the course overview. Presence of this marker means that the requesting student's
 * relevant batch has started; batch ids, passwords, timing details and creator information are not needed there.
 *
 * @param started always {@code true}; an exercise with no started relevant batch receives an empty set instead
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchOverviewDTO(boolean started) {

    public static final QuizBatchOverviewDTO STARTED = new QuizBatchOverviewDTO(true);
}
