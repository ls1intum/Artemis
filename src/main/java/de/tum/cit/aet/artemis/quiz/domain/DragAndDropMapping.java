package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A DragAndDropMapping links a {@link DragItem} to a {@link DropLocation}.
 * <p>
 * This is the in-memory / wire representation used by the REST DTOs, the raw exam submission/conduction endpoints, the scoring strategies and the client. It carries the drag item
 * and
 * drop location as nested objects (the shape the client expects), plus derived, transient {@code dragItemIndex}/{@code dropLocationIndex} for the editor UI.
 * <p>
 * It is intentionally <b>not</b> what gets persisted: a question's correct mappings are stored inside {@link DragAndDropQuestionContent} as normalized, id-based
 * {@link DragAndDropCorrectMapping} entries, and a submission's mappings are stored inside {@link DragAndDropSubmittedAnswerSelection} as {@link DragAndDropMappingSelection}
 * entries.
 * {@link DragAndDropQuestion#getCorrectMappings()} and {@link DragAndDropSubmittedAnswer#getMappings()} build these object-based mappings on demand by resolving the stored ids
 * against
 * the owning question, and their setters extract the ids back into the stored form. Formerly a JPA entity backed by {@code drag_and_drop_mapping}; it is now a plain POJO.
 * <p>
 * It still extends {@link DomainObject} to reuse the {@code id} field and its id-based {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this class
 * is no
 * longer an {@code @Entity}. For a question's correct mapping the {@code id} is the question-scoped mapping id; for a submission mapping it is {@code null} (submission mappings
 * have no
 * identity of their own).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropMapping extends DomainObject {

    // Derived, transient: the position of the drag item / drop location within the question's ordered lists. Populated when building the mapping from stored content; never
    // persisted.
    private Integer dragItemIndex;

    private Integer dropLocationIndex;

    private Boolean invalid = false;

    private DragItem dragItem;

    private DropLocation dropLocation;

    public Integer getDragItemIndex() {
        return dragItemIndex;
    }

    public void setDragItemIndex(Integer dragItemIndex) {
        this.dragItemIndex = dragItemIndex;
    }

    public Integer getDropLocationIndex() {
        return dropLocationIndex;
    }

    public void setDropLocationIndex(Integer dropLocationIndex) {
        this.dropLocationIndex = dropLocationIndex;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public DragItem getDragItem() {
        return dragItem;
    }

    public void setDragItem(DragItem dragItem) {
        this.dragItem = dragItem;
    }

    public DragAndDropMapping dragItem(DragItem dragItem) {
        this.dragItem = dragItem;
        return this;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public void setDropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
    }

    public DragAndDropMapping dropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
        return this;
    }

    @Override
    public String toString() {
        return "DragAndDropMapping{" + "id=" + getId() + ", dragItemIndex=" + dragItemIndex + ", dropLocationIndex=" + dropLocationIndex + ", invalid=" + isInvalid() + "}";
    }
}
