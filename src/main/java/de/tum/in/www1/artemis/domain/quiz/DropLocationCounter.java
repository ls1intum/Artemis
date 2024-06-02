package de.tum.in.www1.artemis.domain.quiz;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * A DropLocationCounter.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocationCounter extends TempIdObject implements QuizQuestionStatisticComponent<DragAndDropQuestionStatistic, DropLocation, DragAndDropQuestion> {

    @JsonIgnore
    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    private DropLocation dropLocation;

    private Integer ratedCounter = 0;

    private Integer unRatedCounter = 0;

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
        setDragAndDropQuestionStatistic(dragAndDropQuestionStatistic);
    }

    @Override
    @JsonIgnore
    public DropLocation getQuizQuestionComponent() {
        return getDropLocation();
    }

    @Override
    @JsonIgnore
    public void setQuizQuestionComponent(DropLocation dropLocation) {
        setDropLocation(dropLocation);
    }

    @Override
    public String toString() {
        return "DropLocationCounter{" + "id=" + getId() + "}";
    }

    public Integer getRatedCounter() {
        return ratedCounter;
    }

    public void setRatedCounter(Integer ratedCounter) {
        this.ratedCounter = ratedCounter;
    }

    public Integer getUnRatedCounter() {
        return unRatedCounter;
    }

    public void setUnRatedCounter(Integer unRatedCounter) {
        this.unRatedCounter = unRatedCounter;
    }
}
