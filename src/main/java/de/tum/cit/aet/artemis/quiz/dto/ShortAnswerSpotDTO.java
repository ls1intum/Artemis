package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

public record ShortAnswerSpotDTO(Long id, Integer spotNr, Integer width, Boolean invalid) {

    public static ShortAnswerSpotDTO of(ShortAnswerSpot spot) {
        return new ShortAnswerSpotDTO(spot.getId(), spot.getSpotNr(), spot.getWidth(), spot.isInvalid());
    }

}
