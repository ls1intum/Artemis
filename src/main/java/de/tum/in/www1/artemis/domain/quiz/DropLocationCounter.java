package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DropLocationCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocationCounter extends QuizStatisticCounter implements QuizQuestionStatisticComponent<DragAndDropQuestionStatistic, DropLocation, DragAndDropQuestion> {

    @JsonIgnore
    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    private DropLocation dropLocation;

    public DragAndDropQuestionStatistic getDragAndDropQuestionStatistic() {
        return dragAndDropQuestionStatistic;
    }

    public void setDragAndDropQuestionStatistic(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
        this.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public void setDropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionStatistic(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
    }

    @Override
    @JsonIgnore
    public DropLocation getQuizQuestionComponent() {
        return null;
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(DropLocation dropLocation) {

    }

    @Override
    public String toString() {
        return "DropLocationCounter{" + "id=" + getId() + "}";
    }
}
