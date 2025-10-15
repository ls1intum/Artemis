package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ScoringType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionReEvaluateDTO(@NotNull Long id, @NotEmpty String title, @NotNull ScoringType scoringType, @NotNull Boolean randomizeOrder,
        @NotNull Boolean invalid, String text, String hint, String explanation, @NotEmpty List<@Valid AnswerOptionReEvaluateDTO> answerOptions)
        implements QuizQuestionReEvaluateDTO {
}
