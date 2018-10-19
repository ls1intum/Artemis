package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A DropLocationCounter.
 */
@Entity
@Table(name = "drop_location_counter")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DropLocationCounter implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne    @JoinColumn(unique = true)
    private DropLocation dropLocation;

    @ManyToOne
    @JsonIgnoreProperties("dropLocationCounters")
    private DragAndDropQuestionStatistic dragAndDropQuestionStatistic;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
