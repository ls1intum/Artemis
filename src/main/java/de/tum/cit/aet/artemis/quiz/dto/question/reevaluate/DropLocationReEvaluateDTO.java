package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DropLocationReEvaluateDTO(@NotNull Long id, @NotNull Boolean invalid) {

    public static DropLocationReEvaluateDTO of(DropLocation dropLocation) {
        return new DropLocationReEvaluateDTO(dropLocation.getId(), dropLocation.isInvalid());
    }
}
