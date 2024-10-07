package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

// Custom object for sql query
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizSubmittedAnswerCount(long count, long quizSubmissionId, long participationId) {
}
