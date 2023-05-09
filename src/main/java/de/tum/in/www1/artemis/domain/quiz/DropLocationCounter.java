package de.tum.in.www1.artemis.domain.quiz;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DropLocationCounter.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DropLocationCounter extends QuizStatisticCounter implements QuizQuestionStatisticComponent<DragAndDropQuestionStatistic, DropLocation, DragAndDropQuestion> {

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    @OneToOne(cascade = { CascadeType.PERSIST })
    @JoinColumn(unique = true)
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
}
