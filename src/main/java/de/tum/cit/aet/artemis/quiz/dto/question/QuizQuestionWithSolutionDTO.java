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
public record QuizQuestionWithSolutionDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @Nullable @JsonUnwrapped MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO,
        @Nullable @JsonUnwrapped DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO,
        @Nullable @JsonUnwrapped ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO) {

    /**
     * Creates a QuizQuestionWithSolutionDTO object from a QuizQuestion object.
     *
     * @param quizQuestion the QuizQuestion object
     * @return the created QuizQuestionWithSolutionDTO object
     */
    public static QuizQuestionWithSolutionDTO of(final QuizQuestion quizQuestion) {
        QuizQuestionBaseDTO quizQuestionBaseDTO = QuizQuestionBaseDTO.of(quizQuestion);
        MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionDTO = null;
        DragAndDropQuestionWithSolutionDTO dragAndDropQuestionDTO = null;
        ShortAnswerQuestionWithMappingDTO shortAnswerQuestionDTO = null;
        switch (quizQuestion) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestionDTO = MultipleChoiceQuestionWithSolutionDTO.of(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestionDTO = DragAndDropQuestionWithSolutionDTO.of(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestionDTO = ShortAnswerQuestionWithMappingDTO.of(shortAnswerQuestion);
            default -> {
                // TODO: Potentially figure out what to do here
            }
        }
        return new QuizQuestionWithSolutionDTO(quizQuestionBaseDTO, quizQuestion.getExplanation(), multipleChoiceQuestionDTO, dragAndDropQuestionDTO, shortAnswerQuestionDTO);
    }

}
