package de.tum.cit.aet.artemis.quiz.dto.question;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

// Note: Only one of the three questions will be non-null depending on the question type
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionWithoutSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithoutSolutionDTO multipleChoiceQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithoutSolutionDTO dragAndDropQuestionWithoutSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO) {

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
