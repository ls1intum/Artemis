package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionDuringDTO(Long id, String title, String text, String hint, Double points, ScoringType scoringType, Boolean randomizeOrder, Boolean invalid,
        Long quizGroupId) {

    public static QuizQuestionDuringDTO of(QuizQuestion quizQuestion) {
        return new QuizQuestionDuringDTO(quizQuestion.getId(), quizQuestion.getTitle(), quizQuestion.getText(), quizQuestion.getHint(), quizQuestion.getPoints(),
                quizQuestion.getScoringType(), quizQuestion.isRandomizeOrder(), quizQuestion.isInvalid(), quizQuestion.getQuizGroupId());
    }

}
