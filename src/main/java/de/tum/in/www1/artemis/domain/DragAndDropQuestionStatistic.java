package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "dnd_question_statistic")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropQuestionStatistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "dragAndDropQuestionStatistic")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DropLocationCounter> dropLocationCounters = new HashSet<>();
    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
