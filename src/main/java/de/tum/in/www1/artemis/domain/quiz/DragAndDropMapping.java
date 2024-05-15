package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A DragAndDropMapping.
 */
public class DragAndDropMapping extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion>, Serializable {

    private Integer dragItemIndex;

    private Integer dropLocationIndex;

    private Boolean invalid = false;

    private DragItem dragItem;

    private DropLocation dropLocation;

    private DragAndDropSubmittedAnswer submittedAnswer;

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

    public DragAndDropMapping dragItem(DragItem dragItem) {
        this.dragItem = dragItem;
        return this;
    }

    public void setDragItem(DragItem dragItem) {
        this.dragItem = dragItem;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public DragAndDropMapping dropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
        return this;
    }

    public void setDropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
    }

    public DragAndDropSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
    }

    @Override
    public String toString() {
        return "DragAndDropMapping{" + "id=" + getId() + ", dragItemIndex='" + getDragItemIndex() + "'" + ", dropLocationIndex='" + getDropLocationIndex() + "'" + ", invalid='"
                + isInvalid() + "'" + "}";
    }

    @Override
    public void setQuestion(DragAndDropQuestion quizQuestion) {

    }
}
