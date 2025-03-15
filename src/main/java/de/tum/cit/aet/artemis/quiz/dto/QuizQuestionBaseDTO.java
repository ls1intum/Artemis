package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionBaseDTO(Long id, String title, String text, String hint, double points, ScoringType scoringType, Boolean randomizeOrder, Boolean invalid,
        Long quizGroupId) {

    public static QuizQuestionBaseDTO of(QuizQuestion quizQuestion) {
        return new QuizQuestionBaseDTO(quizQuestion.getId(), quizQuestion.getTitle(), quizQuestion.getText(), quizQuestion.getHint(), quizQuestion.getPoints(),
                quizQuestion.getScoringType(), quizQuestion.isRandomizeOrder(), quizQuestion.isInvalid(), quizQuestion.getQuizGroupId());
    }

}
