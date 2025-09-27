package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotCreateDTO(long tempID, int spotNr, int width) {

    /**
     * Converts this DTO to a {@link ShortAnswerSpot} domain object.
     * <p>
     * Maps the DTO properties directly to the corresponding fields in the domain object,
     * including temporary ID, spot number, and width.
     *
     * @return the {@link ShortAnswerSpot} domain object with properties set from this DTO
     */
    public ShortAnswerSpot toDomainObject() {
        ShortAnswerSpot spot = new ShortAnswerSpot();
        spot.setTempID(tempID);
        spot.setSpotNr(spotNr);
        spot.setWidth(width);
        return spot;
    }
}
