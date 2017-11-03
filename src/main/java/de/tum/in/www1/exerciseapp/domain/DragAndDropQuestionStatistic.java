package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A DragAndDropQuestionStatistic.
 */
@Entity
@DiscriminatorValue(value="DD")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("drag-and-drop")
public class DragAndDropQuestionStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "dragAndDropQuestionStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> ratedDropLocationCounters = new HashSet<>();

    @OneToMany(mappedBy = "dragAndDropQuestionStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> unRatedDropLocationCounters = new HashSet<>();

    public Set<DropLocationCounter> getRatedDropLocationCounters() {
        return ratedDropLocationCounters;
    }

    public DragAndDropQuestionStatistic ratedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.ratedDropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropQuestionStatistic addRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.ratedDropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(this);
        return this;
    }

    public DragAndDropQuestionStatistic removeRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.ratedDropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(null);
        return this;
    }

    public void setRatedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.ratedDropLocationCounters = dropLocationCounters;
    }

    public Set<DropLocationCounter> getUnRatedDropLocationCounters() {
        return unRatedDropLocationCounters;
    }

    public DragAndDropQuestionStatistic unRatedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.unRatedDropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropQuestionStatistic addUnRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.unRatedDropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(this);
        return this;
    }

    public DragAndDropQuestionStatistic removeUnRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.unRatedDropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(null);
        return this;
    }

    public void setUnRatedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.unRatedDropLocationCounters = dropLocationCounters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = (DragAndDropQuestionStatistic) o;
        if (dragAndDropQuestionStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropQuestionStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropQuestionStatistic{" +
            "id=" + getId() +
            "}";
    }
}
