package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSolutionReEvaluateDTO(@NotNull Long id, @NotBlank String text, @NotNull Boolean invalid) {
}
