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
    private Set<DropLocationCounter> dropLocationCounters = new HashSet<>();


    public Set<DropLocationCounter> getDropLocationCounters() {
        return dropLocationCounters;
    }

    public DragAndDropQuestionStatistic dropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters;
        return this;
    }

    public DragAndDropQuestionStatistic addDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.add(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(this);
        return this;
    }

    public DragAndDropQuestionStatistic removeDropLocationCounters(DropLocationCounter dropLocationCounter) {
        this.dropLocationCounters.remove(dropLocationCounter);
        dropLocationCounter.setDragAndDropQuestionStatistic(null);
        return this;
    }

    public void setDropLocationCounters(Set<DropLocationCounter> dropLocationCounters) {
        this.dropLocationCounters = dropLocationCounters;
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
    @Override
    public void addResult(SubmittedAnswer submittedAnswer, boolean rated){
        //TO-DO
    }
}
