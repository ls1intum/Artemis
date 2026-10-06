package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The id of a stored submitted answer and the id of the question it answers.
 *
 * @param questionId the id of the question
 * @param answerId   the id of the stored answer to the question
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StoredSubmittedAnswerIdDTO(Long questionId, Long answerId) {
}
