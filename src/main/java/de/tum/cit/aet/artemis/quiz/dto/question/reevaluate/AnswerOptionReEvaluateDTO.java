package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionReEvaluateDTO(@NotNull Long id, @NotNull String text, String hint, String explanation, @NotNull Boolean isCorrect, @NotNull Boolean invalid) {

    public static AnswerOptionReEvaluateDTO of(AnswerOption answerOption) {
        return new AnswerOptionReEvaluateDTO(answerOption.getId(), answerOption.getText(), answerOption.getHint(), answerOption.getExplanation(), answerOption.isIsCorrect(),
                answerOption.isInvalid());
    }
}
