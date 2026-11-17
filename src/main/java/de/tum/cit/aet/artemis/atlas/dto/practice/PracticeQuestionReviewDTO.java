package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeQuestionReviewDTO(PracticeQuestionDTO question, boolean isAdaptive, Double score, String submittedAnswer, ZonedDateTime answeredAt) {
}
