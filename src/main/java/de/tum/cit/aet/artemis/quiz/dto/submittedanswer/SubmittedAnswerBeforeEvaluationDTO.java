package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmittedAnswerBeforeEvaluationDTO(Long id, QuizQuestionWithoutSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer, @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {

    public static SubmittedAnswerBeforeEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        MultipleChoiceSubmittedAnswerWithoutSolutionDTO multipleChoiceSubmittedAnswer = null;
        DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer = null;
        ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer = null;
        switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer1 ->
                multipleChoiceSubmittedAnswer = MultipleChoiceSubmittedAnswerWithoutSolutionDTO.of(multipleChoiceSubmittedAnswer1);
            case DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer1 -> dragAndDropSubmittedAnswer = DragAndDropSubmittedAnswerDTO.of(dragAndDropSubmittedAnswer1);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 -> shortAnswerSubmittedAnswer = ShortAnswerSubmittedAnswerDTO.of(shortAnswerSubmittedAnswer1);
            default -> {
            }
        }
        return new SubmittedAnswerBeforeEvaluationDTO(submittedAnswer.getId(), QuizQuestionWithoutSolutionDTO.of(submittedAnswer.getQuizQuestion()), multipleChoiceSubmittedAnswer,
                dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer);

    }

}
