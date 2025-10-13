package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DropLocationCreateDTO(@NotNull Long tempID, @NotNull Double posX, @NotNull Double posY, @NotNull @Positive Double width, @NotNull @Positive Double height) {

    /**
     * Converts this DTO to a {@link DropLocation} domain object.
     * <p>
     * Maps the DTO properties directly to the corresponding fields in the domain object,
     * including temporary ID and positional/dimensional attributes.
     *
     * @return the {@link DropLocation} domain object with properties set from this DTO
     */
    public DropLocation toDomainObject() {
        DropLocation dragItem = new DropLocation();
        dragItem.setTempID(tempID);
        dragItem.setPosX(posX);
        dragItem.setPosY(posY);
        dragItem.setWidth(width);
        dragItem.setHeight(height);
        return dragItem;
    }

    /**
     * Creates a {@link DropLocationCreateDTO} from the given {@link DropLocation} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields, including temporary ID
     * and positional/dimensional attributes.
     *
     * @param dropLocation the {@link DropLocation} domain object to convert
     * @return the {@link DropLocationCreateDTO} with properties set from the domain object
     */
    public static DropLocationCreateDTO of(DropLocation dropLocation) {
        return new DropLocationCreateDTO(dropLocation.getTempID(), dropLocation.getPosX(), dropLocation.getPosY(), dropLocation.getWidth(), dropLocation.getHeight());
    }
}
