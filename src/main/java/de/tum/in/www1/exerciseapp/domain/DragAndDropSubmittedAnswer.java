package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
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

    /**
     * Check if a dragItem or dropLocation were deleted and delete reference to in mappings
     * @param question the changed question with the changed DragItems and DropLocations
     */
    private void checkAndDeleteMappings(DragAndDropQuestion question) {

        if( question != null) {
            // Check if a dragItem or dropLocation was deleted and delete reference to it in mappings
            Set<DragAndDropMapping> selectedMappingsToDelete = new HashSet<>();
            for (DragAndDropMapping mapping : this.getMappings()) {
                if ((!question.getDragItems().contains(mapping.getDragItem())) ||
                    (!question.getDropLocations().contains(mapping.getDropLocation()))) {
                    selectedMappingsToDelete.add(mapping);
                }
            }
            for (DragAndDropMapping mappingToDelete: selectedMappingsToDelete) {
                this.removeMappings(mappingToDelete);
            }
        }
    }

    /**
     * Delete all references to question, dragItems and dropLocations if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    public void checkAndDeleteReferences (QuizExercise quizExercise) {

        // Delete all references to question, dropLocations and dragItem if the question was deleted
        if (!quizExercise.getQuestions().contains(getQuestion())) {
            setQuestion(null);
            mappings = null;
        } else {
            // find same question in quizExercise
            Question question = quizExercise.findQuestionById(getQuestion().getId());

            // Check if a dragItem or dropLocation was deleted and delete the mappings with it
            checkAndDeleteMappings((DragAndDropQuestion) question);
        }
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
