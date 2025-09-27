package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingCreateDTO(long dragItemTempId, long dropLocationTempId) {

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
}
