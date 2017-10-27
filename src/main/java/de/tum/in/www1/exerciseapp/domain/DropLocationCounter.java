package de.tum.in.www1.exerciseapp.domain;

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

    @Column(name = "counter")
    private Integer counter;

    @ManyToOne
    private DragAndDropStatistic dragAndDropStatistic;

    @OneToOne
    @JoinColumn(unique = true)
    private DropLocation dropLocation;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getCounter() {
        return counter;
    }

    public DropLocationCounter counter(Integer counter) {
        this.counter = counter;
        return this;
    }

    public void setCounter(Integer counter) {
        this.counter = counter;
    }

    public DragAndDropStatistic getDragAndDropStatistic() {
        return dragAndDropStatistic;
    }

    public DropLocationCounter dragAndDropStatistic(DragAndDropStatistic dragAndDropStatistic) {
        this.dragAndDropStatistic = dragAndDropStatistic;
        return this;
    }

    public void setDragAndDropStatistic(DragAndDropStatistic dragAndDropStatistic) {
        this.dragAndDropStatistic = dragAndDropStatistic;
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
            ", counter='" + getCounter() + "'" +
            "}";
    }
}
