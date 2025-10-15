package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AnswerOptionReEvaluateDTO(@NotNull Long id, @NotNull String text, String hint, String explanation, @NotNull Boolean isCorrect, @NotNull Boolean invalid) {
}
