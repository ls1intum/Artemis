package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;

public record SubmittedAnswerAfterEvaluationDTO(Long id, QuizQuestionWithSolutionDTO quizQuestion,
        @JsonUnwrapped MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer, @JsonUnwrapped DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer,
        @JsonUnwrapped ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer) {

    public static SubmittedAnswerAfterEvaluationDTO of(final SubmittedAnswer submittedAnswer) {
        MultipleChoiceSubmittedAnswerWithSolutionDTO multipleChoiceSubmittedAnswer = null;
        DragAndDropSubmittedAnswerDTO dragAndDropSubmittedAnswer = null;
        ShortAnswerSubmittedAnswerDTO shortAnswerSubmittedAnswer = null;
        switch (submittedAnswer) {
            case MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer1 ->
                multipleChoiceSubmittedAnswer = MultipleChoiceSubmittedAnswerWithSolutionDTO.of(multipleChoiceSubmittedAnswer1);
            case DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer1 -> dragAndDropSubmittedAnswer = DragAndDropSubmittedAnswerDTO.of(dragAndDropSubmittedAnswer1);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 -> shortAnswerSubmittedAnswer = ShortAnswerSubmittedAnswerDTO.of(shortAnswerSubmittedAnswer1);
            default -> {
            }
        }
        return new SubmittedAnswerAfterEvaluationDTO(submittedAnswer.getId(), QuizQuestionWithSolutionDTO.of(submittedAnswer.getQuizQuestion()), multipleChoiceSubmittedAnswer,
                dragAndDropSubmittedAnswer, shortAnswerSubmittedAnswer);

    }

}
