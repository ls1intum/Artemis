package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

/**
 * A submitted short-answer answer after evaluation. The answer projection is unwrapped, so its {@code type} lands
 * at the top level and doubles as the discriminator of {@link SubmittedAnswerAfterEvaluationDTO}.
 *
 * @param id                         the id of the submitted answer
 * @param scoreInPoints              the points this answer scored
 * @param quizQuestion               the question this answer belongs to, including its solution
 * @param shortAnswerSubmittedAnswer the submitted texts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) implements SubmittedAnswerAfterEvaluationDTO {
}
