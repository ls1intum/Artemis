package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.exerciseapp.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DragAndDropSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value="DD")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeName("drag-and-drop")
public class DragAndDropSubmittedAnswer extends SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "submitted_answer_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonView(QuizView.Before.class)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public DragAndDropSubmittedAnswer mappings(Set<DragAndDropMapping> dragAndDropMappings) {
        this.mappings = dragAndDropMappings;
        return this;
    }

    public DragAndDropSubmittedAnswer addMappings(DragAndDropMapping dragAndDropMapping) {
        this.mappings.add(dragAndDropMapping);
        dragAndDropMapping.setSubmittedAnswer(this);
        return this;
    }

    public DragAndDropSubmittedAnswer removeMappings(DragAndDropMapping dragAndDropMapping) {
        this.mappings.remove(dragAndDropMapping);
        dragAndDropMapping.setSubmittedAnswer(null);
        return this;
    }

    public void setMappings(Set<DragAndDropMapping> dragAndDropMappings) {
        this.mappings = dragAndDropMappings;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    /**
     * Get the drag item that was drag-and-dropped on the given drop location
     *
     * @param dropLocation the drop location
     * @return the selected drag item for the given drop location
     *         (may be null if no drag item was dropped on this drop location)
     */
    public DragItem getSelectedDragItemForDropLocation(DropLocation dropLocation) {
        for (DragAndDropMapping mapping : mappings) {
            if (mapping.getDropLocation().equals(dropLocation)) {
                return mapping.getDragItem();
            }
        }
        return null;
    }

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
