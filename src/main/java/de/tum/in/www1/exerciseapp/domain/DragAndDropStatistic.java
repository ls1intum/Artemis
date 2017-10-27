package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A DragAndDropStatistic.
 */
@Entity
@DiscriminatorValue(value="DD")
//@Table(name = "drag_and_drop_statistic")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropStatistic extends QuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "dragAndDropStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> ratedDropLocationCounters = new HashSet<>();

    @OneToMany(mappedBy = "dragAndDropStatistic")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> unRatedDropLocationCounters = new HashSet<>();


    public Set<DropLocationCounter> getRatedDropLocationCounters() {
        return ratedDropLocationCounters;
    }

    public DragAndDropStatistic ratedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.ratedDropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropStatistic addRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.ratedDropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropStatistic(this);
        return this;
    }

    public DragAndDropStatistic removeRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.ratedDropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropStatistic(null);
        return this;
    }

    public void setRatedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.ratedDropLocationCounters = dropLocationCounters;
    }

    public Set<DropLocationCounter> getUnRatedDropLocationCounters() {
        return unRatedDropLocationCounters;
    }

    public DragAndDropStatistic unRatedDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.unRatedDropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropStatistic addUnRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.unRatedDropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropStatistic(this);
        return this;
    }

    public DragAndDropStatistic removeUnRatedDropLocationCounter(DropLocationCounter dropLocationCounter) {
        this.unRatedDropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropStatistic(null);
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
        DragAndDropStatistic dragAndDropStatistic = (DragAndDropStatistic) o;
        if (dragAndDropStatistic.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropStatistic.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropStatistic{" +
            "id=" + getId() +
            "}";
    }
}
