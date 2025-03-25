package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingDTO(Long id, Integer dragItemIndex, Integer dropLocationIndex, Boolean invalid, DragItemDTO dragItem, DropLocationDTO dropLocation) {

    public static DragAndDropMappingDTO of(DragAndDropMapping dragAndDropMapping) {
        return new DragAndDropMappingDTO(dragAndDropMapping.getId(), dragAndDropMapping.getDragItemIndex(), dragAndDropMapping.getDropLocationIndex(),
                dragAndDropMapping.isInvalid(), DragItemDTO.of(dragAndDropMapping.getDragItem()), DropLocationDTO.of(dragAndDropMapping.getDropLocation()));
    }

}
