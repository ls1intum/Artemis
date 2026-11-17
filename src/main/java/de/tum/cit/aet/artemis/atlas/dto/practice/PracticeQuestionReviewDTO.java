package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeQuestionReviewDTO(QuizQuestionWithSolutionDTO question, boolean isAdaptive, Double score, String submittedAnswer, ZonedDateTime answeredAt) {
}
