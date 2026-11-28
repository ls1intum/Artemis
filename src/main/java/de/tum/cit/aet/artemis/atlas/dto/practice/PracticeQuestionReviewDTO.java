package de.tum.cit.aet.artemis.atlas.dto.practice;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeQuestionReviewDTO(PracticeQuestionDTO question, Double score, String submittedAnswer) {
}
