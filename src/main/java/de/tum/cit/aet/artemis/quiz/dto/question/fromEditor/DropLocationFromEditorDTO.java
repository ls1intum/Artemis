package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

/**
 * DTO for drop locations in the editor context.
 * Supports both creating new locations (id is null) and updating existing locations (id is non-null).
 *
 * @param id     the ID of the drop location, null for new locations
 * @param tempID the temporary ID for matching during creation (can be null for persisted entities, will use id instead)
 * @param posX   the X position
 * @param posY   the Y position
 * @param width  the width
 * @param height the height
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DropLocationFromEditorDTO(Long id, Long tempID, @NotNull Double posX, @NotNull Double posY, @NotNull Double width, @NotNull Double height) {

    /**
     * Creates a DropLocationFromEditorDTO from the given DropLocation domain object.
     * For persisted entities, uses the id as tempID if tempID is null.
     *
     * @param dropLocation the drop location to convert
     * @return the corresponding DTO
     */
    public static DropLocationFromEditorDTO of(DropLocation dropLocation) {
        // Use id as tempID fallback for persisted entities
        Long effectiveTempID = dropLocation.getTempID() != null ? dropLocation.getTempID() : dropLocation.getId();
        return new DropLocationFromEditorDTO(dropLocation.getId(), effectiveTempID, dropLocation.getPosX(), dropLocation.getPosY(), dropLocation.getWidth(),
                dropLocation.getHeight());
    }

    /**
     * Creates a new DropLocation domain object from this DTO.
     *
     * @return a new DropLocation domain object
     */
    public DropLocation toDomainObject() {
        DropLocation dropLocation = new DropLocation();
        // Use id as tempID fallback for mapping resolution
        dropLocation.setTempID(tempID != null ? tempID : id);
        dropLocation.setPosX(posX);
        dropLocation.setPosY(posY);
        dropLocation.setWidth(width);
        dropLocation.setHeight(height);
        return dropLocation;
    }

    /**
     * Applies the DTO values to an existing DropLocation entity.
     *
     * @param dropLocation the existing drop location to update
     */
    public void applyTo(DropLocation dropLocation) {
        dropLocation.setTempID(tempID != null ? tempID : id);
        dropLocation.setPosX(posX);
        dropLocation.setPosY(posY);
        dropLocation.setWidth(width);
        dropLocation.setHeight(height);
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
