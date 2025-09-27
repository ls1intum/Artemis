package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotCreateDTO(long tempID, int spotNr, int width) {

    public ShortAnswerSpot toDomainObject() {
        ShortAnswerSpot spot = new ShortAnswerSpot();
        spot.setTempID(tempID);
        spot.setSpotNr(spotNr);
        spot.setWidth(width);
        return spot;
    }
}
