package de.tum.cit.aet.artemis.quiz.dto.question.create;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionCreateDTO(@NotNull String title, String text, String hint, String explanation, double points, ScoringType scoringType, boolean randomizeOrder,
        List<AnswerOptionCreateDTO> answerOptions, boolean singleChoice) implements QuizQuestionCreateDTO {

}
