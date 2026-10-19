package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The "correct answer" content of a {@link DragAndDropQuestion}: its drop locations, drag items and correct mappings. Stored inside the {@code quiz_question.content} JSON column.
 * <p>
 * This is a plain POJO (not a JPA entity). It is an internal storage representation and is never serialized directly to the client: {@link DragAndDropQuestion} keeps its existing
 * getters ({@code getDropLocations()} / {@code getDragItems()} / {@code getCorrectMappings()}) which delegate here, preserving the REST/websocket wire format.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class DragAndDropQuestionContent implements QuizQuestionContent {

    @JsonProperty("dropLocations")
    private List<DropLocation> dropLocations = new ArrayList<>();

    @JsonProperty("dragItems")
    private List<DragItem> dragItems = new ArrayList<>();

    @JsonProperty("correctMappings")
    private List<DragAndDropCorrectMapping> correctMappings = new ArrayList<>();

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations != null ? dropLocations : new ArrayList<>();
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public void setDragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems != null ? dragItems : new ArrayList<>();
    }

    public List<DragAndDropCorrectMapping> getCorrectMappings() {
        return correctMappings;
    }

    public void setCorrectMappings(List<DragAndDropCorrectMapping> correctMappings) {
        this.correctMappings = correctMappings != null ? correctMappings : new ArrayList<>();
    }

    @Override
    public Set<Long> componentIds() {
        Set<Long> ids = new HashSet<>();
        for (DropLocation dropLocation : dropLocations) {
            if (dropLocation.getId() != null) {
                ids.add(dropLocation.getId());
            }
        }
        for (DragItem dragItem : dragItems) {
            if (dragItem.getId() != null) {
                ids.add(dragItem.getId());
            }
        }
        for (DragAndDropCorrectMapping mapping : correctMappings) {
            if (mapping.getId() != null) {
                ids.add(mapping.getId());
            }
        }
        return ids;
    }

    /**
     * Value equality by persisted JSON. See {@link QuizQuestionContent#haveEqualPersistedForm} for why this is needed
     * and why it is not delegated to the nested components.
     *
     * @param other the object to compare with
     * @return true if both would be persisted as the same JSON
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof DragAndDropQuestionContent && QuizQuestionContent.haveEqualPersistedForm(this, (DragAndDropQuestionContent) other);
    }

    @Override
    public int hashCode() {
        return QuizQuestionContent.persistedFormHashCode(this);
    }
}
