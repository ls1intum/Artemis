package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * The normalized, persisted form of a correct drag-and-drop mapping: it states that the drag item with id {@link #dragItemId} is a correct answer for the drop location with id
 * {@link #dropLocationId}. Stored inside {@link DragAndDropQuestionContent#getCorrectMappings()} in the {@code quiz_question.content} JSON column.
 * <p>
 * This is an internal storage type (never serialized to the client directly). The object-based {@link DragAndDropMapping} is the wire/in-memory representation;
 * {@link DragAndDropQuestion#getCorrectMappings()} / {@code setCorrectMappings(...)} convert between the two, resolving ids against the question's drag items and drop locations.
 * <p>
 * It extends {@link DomainObject} to reuse the {@code id} field (the question-scoped mapping id, used by re-evaluation to match mappings across an edit) and its id-based
 * {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this is a plain JSON-embedded POJO, not an {@code @Entity}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropCorrectMapping extends DomainObject {

    @JsonProperty("dragItemId")
    private Long dragItemId;

    @JsonProperty("dropLocationId")
    private Long dropLocationId;

    @JsonProperty("invalid")
    private Boolean invalid;

    public DragAndDropCorrectMapping() {
    }

    public DragAndDropCorrectMapping(Long id, Long dragItemId, Long dropLocationId, Boolean invalid) {
        setId(id);
        this.dragItemId = dragItemId;
        this.dropLocationId = dropLocationId;
        this.invalid = invalid;
    }

    public Long getDragItemId() {
        return dragItemId;
    }

    public void setDragItemId(Long dragItemId) {
        this.dragItemId = dragItemId;
    }

    public Long getDropLocationId() {
        return dropLocationId;
    }

    public void setDropLocationId(Long dropLocationId) {
        this.dropLocationId = dropLocationId;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }
}
