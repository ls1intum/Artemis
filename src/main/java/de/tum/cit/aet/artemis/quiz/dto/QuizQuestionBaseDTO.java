package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizQuestionBaseDTO(Long id, String title, String text, String hint, double points, ScoringType scoringType, Boolean randomizeOrder, Boolean invalid, Long quizGroupId,
        String type) {

    public static QuizQuestionBaseDTO of(QuizQuestion quizQuestion) {
        String type;
        switch (quizQuestion) {
            case MultipleChoiceQuestion ignored -> type = "multiple-choice";
            case DragAndDropQuestion ignored -> type = "drag-and-drop";
            case ShortAnswerQuestion ignored -> type = "short-answer";
            default -> type = "";
        }
        return new QuizQuestionBaseDTO(quizQuestion.getId(), quizQuestion.getTitle(), quizQuestion.getText(), quizQuestion.getHint(), quizQuestion.getPoints(),
                quizQuestion.getScoringType(), quizQuestion.isRandomizeOrder(), quizQuestion.isInvalid(), quizQuestion.getQuizGroupId(), type);
    }

}
