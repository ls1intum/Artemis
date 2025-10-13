package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotCreateDTO(@NotNull Long tempID, @NotNull Integer spotNr, @NotNull @Positive Integer width) {

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

    /**
     * Creates a {@link ShortAnswerSpotCreateDTO} from the given {@link ShortAnswerSpot} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields, including temporary ID,
     * spot number, and width.
     *
     * @param spot the {@link ShortAnswerSpot} domain object to convert
     * @return the {@link ShortAnswerSpotCreateDTO} with properties set from the domain object
     */
    public static ShortAnswerSpotCreateDTO of(ShortAnswerSpot spot) {
        return new ShortAnswerSpotCreateDTO(spot.getTempID(), spot.getSpotNr(), spot.getWidth());
    }
}
