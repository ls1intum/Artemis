package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * The full post-publish projection of a MultipleChoice quiz question. Both components are unwrapped, so the payload is one flat
 * object carrying the shared question fields (including the {@code type} discriminator) and the MultipleChoice-specific ones.
 *
 * @param quizQuestionBaseDTO                   the fields shared by every question type
 * @param explanation                           the explanation shown once solutions are published
 * @param multipleChoiceQuestionWithSolutionDTO the MultipleChoice-specific fields, including the solution
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO) implements QuizQuestionWithSolutionDTO {
}
