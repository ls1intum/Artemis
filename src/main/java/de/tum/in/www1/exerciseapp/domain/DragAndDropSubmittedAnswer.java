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
 * A DragAndDropSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value="DD")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragAndDropSubmittedAnswer extends SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(mappedBy = "submittedAnswer")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropAssignment> assignments = new HashSet<>();

    // jhipster-needle-entity-add-field - Jhipster will add fields here, do not remove
    public Set<DragAndDropAssignment> getAssignments() {
        return assignments;
    }

    public DragAndDropSubmittedAnswer assignments(Set<DragAndDropAssignment> dragAndDropAssignments) {
        this.assignments = dragAndDropAssignments;
        return this;
    }

    public DragAndDropSubmittedAnswer addAssignments(DragAndDropAssignment dragAndDropAssignment) {
        this.assignments.add(dragAndDropAssignment);
        dragAndDropAssignment.setSubmittedAnswer(this);
        return this;
    }

    public DragAndDropSubmittedAnswer removeAssignments(DragAndDropAssignment dragAndDropAssignment) {
        this.assignments.remove(dragAndDropAssignment);
        dragAndDropAssignment.setSubmittedAnswer(null);
        return this;
    }

    public void setAssignments(Set<DragAndDropAssignment> dragAndDropAssignments) {
        this.assignments = dragAndDropAssignments;
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
        DragAndDropSubmittedAnswer dragAndDropSubmittedAnswer = (DragAndDropSubmittedAnswer) o;
        if (dragAndDropSubmittedAnswer.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragAndDropSubmittedAnswer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragAndDropSubmittedAnswer{" +
            "id=" + getId() +
            "}";
    }
}
