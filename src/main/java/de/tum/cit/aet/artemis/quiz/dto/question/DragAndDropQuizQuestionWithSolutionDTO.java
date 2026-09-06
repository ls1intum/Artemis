package de.tum.cit.aet.artemis.quiz.dto.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * The full post-publish projection of a DragAndDrop quiz question. Both components are unwrapped, so the payload is one flat
 * object carrying the shared question fields (including the {@code type} discriminator) and the DragAndDrop-specific ones.
 *
 * @param quizQuestionBaseDTO                the fields shared by every question type
 * @param explanation                        the explanation shown once solutions are published
 * @param dragAndDropQuestionWithSolutionDTO the DragAndDrop-specific fields, including the solution
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropQuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO) implements QuizQuestionWithSolutionDTO {
}
