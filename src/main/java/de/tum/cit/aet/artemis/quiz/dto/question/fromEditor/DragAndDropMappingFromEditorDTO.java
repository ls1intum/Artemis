package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

/**
 * DTO for drag and drop mappings in the editor context.
 * Uses temporary IDs to reference drag items and drop locations.
 * For persisted entities, uses real IDs as fallback when tempID is null.
 *
 * @param id                 the ID of the mapping, null for new mappings
 * @param dragItemTempId     the temporary ID of the associated drag item (can be null for persisted entities, will use real ID)
 * @param dropLocationTempId the temporary ID of the associated drop location (can be null for persisted entities, will use real ID)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingFromEditorDTO(Long id, Long dragItemTempId, Long dropLocationTempId) {

    /**
     * Creates a DragAndDropMappingFromEditorDTO from the given DragAndDropMapping domain object.
     * For persisted entities, uses real IDs as tempIDs when tempID is null.
     *
     * @param mapping the mapping to convert
     * @return the corresponding DTO
     */
    public static DragAndDropMappingFromEditorDTO of(DragAndDropMapping mapping) {
        // Use real ID as fallback for tempID when dealing with persisted entities
        Long dragItemEffectiveId = mapping.getDragItem().getTempID() != null ? mapping.getDragItem().getTempID() : mapping.getDragItem().getId();
        Long dropLocationEffectiveId = mapping.getDropLocation().getTempID() != null ? mapping.getDropLocation().getTempID() : mapping.getDropLocation().getId();
        return new DragAndDropMappingFromEditorDTO(mapping.getId(), dragItemEffectiveId, dropLocationEffectiveId);
    }

    /**
     * Creates a new DragAndDropMapping domain object from this DTO.
     * The mapping contains temporary DragItem and DropLocation objects that need to be resolved later.
     *
     * @return a new DragAndDropMapping domain object
     */
    public DragAndDropMapping toDomainObject() {
        DragAndDropMapping mapping = new DragAndDropMapping();
        DragItem dragItem = new DragItem();
        DropLocation dropLocation = new DropLocation();
        dragItem.setTempID(dragItemTempId);
        dropLocation.setTempID(dropLocationTempId);
        mapping.setDragItem(dragItem);
        mapping.setDropLocation(dropLocation);
        return mapping;
    }
}
