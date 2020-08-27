package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A DropLocationCounter.
 */
@Entity
@DiscriminatorValue(value = "DD")
public class DropLocationCounter extends QuizStatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DropLocationCounter dropLocationCounter = (DropLocationCounter) o;
        if (dropLocationCounter.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dropLocationCounter.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DropLocationCounter{" + "id=" + getId() + "}";
    }
}
