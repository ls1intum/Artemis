package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A DragAndDropAssignment.
 */
@Entity
@Table(name = "drag_and_drop_assignment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnoreProperties("")
    private DragItem item;

    @ManyToOne
    @JsonIgnoreProperties("")
    private DropLocation location;

    @ManyToOne
    @JsonIgnoreProperties("assignments")
    private DragAndDropSubmittedAnswer submittedAnswer;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DragItem getItem() {
        return item;
    }

    public DragAndDropAssignment item(DragItem dragItem) {
        this.item = dragItem;
        return this;
    }

    public void setItem(DragItem dragItem) {
        this.item = dragItem;
    }

    public DropLocation getLocation() {
        return location;
    }

    public DragAndDropAssignment location(DropLocation dropLocation) {
        this.location = dropLocation;
        return this;
    }

    public void setLocation(DropLocation dropLocation) {
        this.location = dropLocation;
    }

    public DragAndDropSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public DragAndDropAssignment submittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
        return this;
    }

    public void setSubmittedAnswer(DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer) {
        this.submittedAnswer = dragAndDropSubmittedAnswer;
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
        DragAndDropAssignment dragAndDropAssignment = (DragAndDropAssignment) o;
        if (dragAndDropAssignment.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropAssignment.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropAssignment{" +
            "id=" + getId() +
            "}";
    }
}
