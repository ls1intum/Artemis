package de.tum.in.www1.exerciseapp.domain;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DropLocationCounter.
 */
@Entity
@DiscriminatorValue(value="DD")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DropLocationCounter extends StatisticCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    @OneToOne
    @JoinColumn(unique = true)
    private DropLocation dropLocation;

    public DragAndDropQuestionStatistic getDragAndDropQuestionStatistic() {
        return dragAndDropQuestionStatistic;
    }

    public DropLocationCounter dragAndDropQuestionStatistic(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
        this.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
        return this;
    }

    public void setDragAndDropQuestionStatistic(DragAndDropQuestionStatistic dragAndDropQuestionStatistic) {
        this.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
    }

    public DropLocation getDropLocation() {
        return dropLocation;
    }

    public DropLocationCounter dropLocation(DropLocation dropLocation) {
        this.dropLocation = dropLocation;
        return this;
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
        return "DropLocationCounter{" +
            "id=" + getId() +
            "}";
    }
}
