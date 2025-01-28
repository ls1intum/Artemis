package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

public record QuizQuestionDTOBefore(String title, String text, String hint, Integer points, ScoringType scoringType, Boolean randomizeOrder, Boolean invalid, Long quizGroupId) {

    public static QuizQuestionDTOBefore of(QuizQuestion quizQuestion) {
        return new QuizQuestionDTOBefore(quizQuestion.getTitle(), quizQuestion.getText(), quizQuestion.getHint(), quizQuestion.getPoints(), quizQuestion.getScoringType(),
                quizQuestion.isRandomizeOrder(), quizQuestion.isInvalid(), quizQuestion.getQuizGroupId());
    }
}
