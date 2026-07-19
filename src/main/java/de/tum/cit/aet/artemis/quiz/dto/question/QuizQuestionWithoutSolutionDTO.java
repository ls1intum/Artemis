package de.tum.cit.aet.artemis.quiz.dto.question;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

// Note: Only one of the three questions will be non-null depending on the question type
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Self-referencing override: Jackson inherits class-level annotations from implemented interfaces, so without this,
// deserializing a declared QuizQuestionWithoutSolutionDTO target would pick up QuizQuestionForExamDTO's
// @JsonDeserialize(as = QuizQuestionWithSolutionDTO.class) and fail ("not a subtype"). This restores the identity
// mapping for this concrete type without touching the interface's default, which other sites still rely on.
@JsonDeserialize(as = QuizQuestionWithoutSolutionDTO.class)
public record QuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) implements QuizQuestionForExamDTO {

    /**
     * Creates a QuizQuestionWithoutSolutionDTO object from a QuizQuestion object.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithoutSolutionDTO object
     */
    public static QuizQuestionWithoutSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO quizQuestionBaseDTO = QuizQuestionBaseDTO.of(quizQuestion);
        MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionDTO = null;
        DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionDTO = null;
        ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionDTO = null;
        switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestionDTO = MultipleChoiceQuestionWithoutSolutionDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestionDTO = DragAndDropQuestionWithoutSolutionDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestionDTO = ShortAnswerQuestionWithoutMappingDTO.of(shortAnswerQuestion);
            default -> {
                // TODO: Potentially figure out what to do here
            }
        }
        return new QuizQuestionWithoutSolutionDTO(quizQuestionBaseDTO, multipleChoiceQuestionDTO, dragAndDropQuestionDTO, shortAnswerQuestionDTO);
    }

}
