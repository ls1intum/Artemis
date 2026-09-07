package de.tum.cit.aet.artemis.quiz.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingDTO(Long id, Integer dragItemIndex, Integer dropLocationIndex, Boolean invalid, DragItemDTO dragItem, DropLocationDTO dropLocation) {

    /**
     * A mapping carries a whole drag item, so it needs the same question id that {@link DragItemDTO#of} needs. A mapping holds no reference back to its question either, so the
     * caller passes it down.
     *
     * @param questionId         the id of the drag and drop question the mapping belongs to, or null while that question has not been inserted
     * @param dragAndDropMapping the mapping to project
     * @return the client-facing view of the mapping
     */
    public static DragAndDropMappingDTO of(@Nullable Long questionId, DragAndDropMapping dragAndDropMapping) {
        return new DragAndDropMappingDTO(dragAndDropMapping.getId(), dragAndDropMapping.getDragItemIndex(), dragAndDropMapping.getDropLocationIndex(),
                dragAndDropMapping.isInvalid(), DragItemDTO.of(questionId, dragAndDropMapping.getDragItem()), DropLocationDTO.of(dragAndDropMapping.getDropLocation()));
    }

}
