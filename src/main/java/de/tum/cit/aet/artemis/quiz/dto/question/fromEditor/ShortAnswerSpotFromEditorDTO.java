package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

/**
 * DTO for short answer spots in the editor context.
 * Supports both creating new spots (id is null) and updating existing spots (id is non-null).
 *
 * @param id     the ID of the spot, null for new spots
 * @param tempID the temporary ID for matching during creation (can be null for persisted entities, will use id instead)
 * @param width  the width of the input field
 * @param spotNr the spot number in the text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSpotFromEditorDTO(Long id, Long tempID, Integer width, @NotNull Integer spotNr) {

    /**
     * Creates a ShortAnswerSpotFromEditorDTO from the given ShortAnswerSpot domain object.
     * For persisted entities, uses the id as tempID if tempID is null.
     *
     * @param spot the spot to convert
     * @return the corresponding DTO
     */
    public static ShortAnswerSpotFromEditorDTO of(ShortAnswerSpot spot) {
        // Use id as tempID fallback for persisted entities
        Long effectiveTempID = spot.getTempID() != null ? spot.getTempID() : spot.getId();
        return new ShortAnswerSpotFromEditorDTO(spot.getId(), effectiveTempID, spot.getWidth(), spot.getSpotNr());
    }

    /**
     * Creates a new ShortAnswerSpot domain object from this DTO.
     *
     * @return a new ShortAnswerSpot domain object
     */
    public ShortAnswerSpot toDomainObject() {
        ShortAnswerSpot spot = new ShortAnswerSpot();
        // Use id as tempID fallback for mapping resolution
        spot.setTempID(tempID != null ? tempID : id);
        spot.setWidth(width);
        spot.setSpotNr(spotNr);
        return spot;
    }

    /**
     * Applies the DTO values to an existing ShortAnswerSpot entity.
     *
     * @param spot the existing spot to update
     */
    public void applyTo(ShortAnswerSpot spot) {
        spot.setTempID(tempID != null ? tempID : id);
        spot.setWidth(width);
        spot.setSpotNr(spotNr);
    }

    /**
     * Gets the effective ID used for mapping resolution (tempID if available, otherwise id).
     *
     * @return the effective ID for matching
     */
    public Long effectiveId() {
        return tempID != null ? tempID : id;
    }
}
