package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragAndDropMappingCreateDTO(@NotNull Long dragItemTempId, @NotNull Long dropLocationTempId) {

    /**
     * Converts this DTO to a {@link DragAndDropMapping} domain object.
     *
     * @return a bare {@link DragAndDropMapping} domain object (mapping resolution happens at question level)
     */
    public DragAndDropMapping toDomainObject() {
        DragAndDropMapping dragAndDropMapping = new DragAndDropMapping();
        return dragAndDropMapping;
    }

    /**
     * Creates a {@link DragAndDropMappingCreateDTO} from the given {@link DragAndDropMapping} domain object.
     *
     * @param mapping the {@link DragAndDropMapping} domain object to convert
     * @return the {@link DragAndDropMappingCreateDTO} with IDs set from the domain object
     */
    public static DragAndDropMappingCreateDTO of(DragAndDropMapping mapping) {
        return new DragAndDropMappingCreateDTO(mapping.getDragItem().getId(), mapping.getDropLocation().getId());
    }
}
