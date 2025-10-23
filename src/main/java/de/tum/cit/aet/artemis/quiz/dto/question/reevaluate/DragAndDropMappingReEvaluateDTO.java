package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingReEvaluateDTO(@NotNull Long dragItemId, @NotNull Long dropLocationId) {

    public static DragAndDropMappingReEvaluateDTO of(DragAndDropMapping dragAndDropMapping) {
        return new DragAndDropMappingReEvaluateDTO(dragAndDropMapping.getDragItem().getId(), dragAndDropMapping.getDropLocation().getId());
    }
}
