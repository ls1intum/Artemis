package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;

/**
 * A submitted multiple-choice answer after evaluation. The answer projection is unwrapped, so its {@code type} lands
 * at the top level and doubles as the discriminator of {@link SubmittedAnswerAfterEvaluationDTO}.
 *
 * @param id                            the id of the submitted answer
 * @param scoreInPoints                 the points this answer scored
 * @param quizQuestion                  the question this answer belongs to, including its solution
 * @param multipleChoiceSubmittedAnswer the selected options, including the correctness flags
 */
@Schema(requiredProperties = { "type" })
@SchemaProperty(name = "type", schema = @Schema(type = "string", allowableValues = { "multiple-choice" }, defaultValue = "multiple-choice"))
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceSubmittedAnswerAfterEvaluationDTO(Long id, Double scoreInPoints, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer) implements SubmittedAnswerAfterEvaluationDTO {
}
