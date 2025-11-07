package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotReEvaluateDTO(@NotNull Long id, @NotNull Boolean invalid) {

    public static ShortAnswerSpotReEvaluateDTO of(ShortAnswerSpot shortAnswerSpot) {
        return new ShortAnswerSpotReEvaluateDTO(shortAnswerSpot.getId(), shortAnswerSpot.isInvalid());
    }
}
