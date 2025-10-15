package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemReEvaluateDTO(@NotNull Long id, @NotNull Boolean invalid, String text, String pictureFilePath) {
}
