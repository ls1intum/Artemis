package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingCreateDTO(@NotNull Long dragItemTempId, @NotNull Long dropLocationTempId) {

    /**
     * Converts this DTO to a {@link DragAndDropMapping} domain object.
     * <p>
     * Creates temporary {@link DragItem} and {@link DropLocation} objects populated
     * with the provided tempIDs and associates them with the mapping. This method is used to
     * initialize mappings prior to resolution with actual domain objects in the question.
     *
     * @return the {@link DragAndDropMapping} domain object with temporary drag item and drop location references
     */
    public DragAndDropMapping toDomainObject() {
        DragAndDropMapping dragAndDropMapping = new DragAndDropMapping();
        DragItem dragItem = new DragItem();
        DropLocation dropLocation = new DropLocation();
        dragItem.setTempID(dragItemTempId);
        dropLocation.setTempID(dropLocationTempId);
        dragAndDropMapping.setDragItem(dragItem);
        dragAndDropMapping.setDropLocation(dropLocation);
        return dragAndDropMapping;
    }

    /**
     * Creates a {@link DragAndDropMappingCreateDTO} from the given {@link DragAndDropMapping} domain object.
     * <p>
     * Maps the temporary IDs of the associated {@link DragItem} and {@link DropLocation}
     * to the corresponding DTO fields.
     *
     * @param mapping the {@link DragAndDropMapping} domain object to convert
     * @return the {@link DragAndDropMappingCreateDTO} with temp IDs set from the domain object
     */
    public static DragAndDropMappingCreateDTO of(DragAndDropMapping mapping) {
        return new DragAndDropMappingCreateDTO(mapping.getDragItem().getTempID(), mapping.getDropLocation().getTempID());
    }
}
