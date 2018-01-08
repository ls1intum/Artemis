package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

/**
 * A DragAndDropQuestion.
 */
@Entity
@DiscriminatorValue(value = "DD")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("drag-and-drop")
public class DragAndDropQuestion extends Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "background_file_path")
    private String backgroundFilePath;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<DropLocation> dropLocations = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @OrderColumn
    @JoinColumn(name = "question_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<DragItem> dragItems = new ArrayList<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove

    public String getBackgroundFilePath() {
        return backgroundFilePath;
    }

    public DragAndDropQuestion backgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
        return this;
    }

    public void setBackgroundFilePath(String backgroundFilePath) {
        this.backgroundFilePath = backgroundFilePath;
    }

    public List<DropLocation> getDropLocations() {
        return dropLocations;
    }

    public DragAndDropQuestion dropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
        return this;
    }

    public DragAndDropQuestion addDropLocations(DropLocation dropLocation) {
        this.dropLocations.add(dropLocation);
        dropLocation.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeDropLocations(DropLocation dropLocation) {
        this.dropLocations.remove(dropLocation);
        dropLocation.setQuestion(null);
        return this;
    }

    public void setDropLocations(List<DropLocation> dropLocations) {
        this.dropLocations = dropLocations;
    }

    public List<DragItem> getDragItems() {
        return dragItems;
    }

    public DragAndDropQuestion dragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
        return this;
    }

    public DragAndDropQuestion addDragItems(DragItem dragItem) {
        this.dragItems.add(dragItem);
        dragItem.setQuestion(this);
        return this;
    }

    public DragAndDropQuestion removeDragItems(DragItem dragItem) {
        this.dragItems.remove(dragItem);
        dragItem.setQuestion(null);
        return this;
    }

    public void setDragItems(List<DragItem> dragItems) {
        this.dragItems = dragItems;
    }
    // jhipster-needle-entity-add-getters-setters - Jhipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragAndDropQuestion dragAndDropQuestion = (DragAndDropQuestion) o;
        if (dragAndDropQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropQuestion{" +
            "id=" + getId() +
            ", backgroundFilePath='" + getBackgroundFilePath() + "'" +
            "}";
    }
}
