package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotDTO(Long id, Integer spotNr, Integer width, Boolean invalid) {

    public static ShortAnswerSpotDTO of(ShortAnswerSpot spot) {
        return new ShortAnswerSpotDTO(spot.getId(), spot.getSpotNr(), spot.getWidth(), spot.isInvalid());
    }

}
