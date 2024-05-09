package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Set;

/**
 * A DropLocation.
 */
public class DropLocation implements QuizQuestionComponent<DragAndDropQuestion>, Serializable {

    private Long id;

    private Double posX;

    private Double posY;

    private Double width;

    private Double height;

    private Boolean invalid = false;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setQuestion(DragAndDropQuestion quizQuestion) {

    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getPosX() {
        return posX;
    }

    public DropLocation posX(Double posX) {
        this.posX = posX;
        return this;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public DropLocation posY(Double posY) {
        this.posY = posY;
        return this;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public Double getWidth() {
        return width;
    }

    public DropLocation width(Double width) {
        this.width = width;
        return this;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return height;
    }

    public DropLocation height(Double height) {
        this.height = height;
        return this;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    /**
     * check if the DropLocation is solved correctly
     *
     * @param dndAnswer Answer from the student with the List of submittedMappings from the Result
     * @return if the drop location is correct
     */
    public boolean isDropLocationCorrect(DragAndDropSubmittedAnswer dndAnswer, DragAndDropQuestion question) {

        Set<DragItem> correctDragItems = question.getCorrectDragItemsForDropLocation(this);
        DragItem selectedDragItem = dndAnswer.getSelectedDragItemForDropLocation(this);

        return ((correctDragItems.isEmpty() && selectedDragItem == null) || (selectedDragItem != null && correctDragItems.contains(selectedDragItem)));
        // this drop location was meant to stay empty and user didn't drag anything onto it
        // OR the user dragged one of the correct drag items onto this drop location
        // => this is correct => Return true;
    }

    @Override
    public String toString() {
        return "DropLocation{" + "id=" + getId() + ", posX='" + getPosX() + "'" + ", posY='" + getPosY() + "'" + ", width='" + getWidth() + "'" + ", height='" + getHeight() + "'"
                + ", invalid='" + isInvalid() + "'" + "}";
    }
}
