package de.tum.in.www1.artemis.domain.quiz;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.quiz.compare.DnDMapping;
import de.tum.in.www1.artemis.domain.view.QuizView;

/**
 * A DragAndDropSubmittedAnswer.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropSubmittedAnswer extends SubmittedAnswer {

    // Specifies that the `selection` field should be stored as JSON in the database.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selection", columnDefinition = "json")
    @JsonView(QuizView.Before.class)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
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

    /**
     * Get the drag item that was drag-and-dropped on the given drop location
     *
     * @param dropLocation the drop location
     * @return the selected drag item for the given drop location (may be null if no drag item was dropped on this drop location)
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
     *
     * @param question the changed question with the changed DragItems and DropLocations
     */
    private void checkAndDeleteMappings(DragAndDropQuestion question) {

        if (question != null) {
            // Check if a dragItem or dropLocation was deleted and delete reference to it in mappings
            Set<DragAndDropMapping> selectedMappingsToDelete = new HashSet<>();
            for (DragAndDropMapping mapping : this.getMappings()) {
                if ((!question.getDragItems().contains(mapping.getDragItem())) || (!question.getDropLocations().contains(mapping.getDropLocation()))) {
                    selectedMappingsToDelete.add(mapping);
                }
            }
            for (DragAndDropMapping mappingToDelete : selectedMappingsToDelete) {
                this.removeMappings(mappingToDelete);
            }
        }
    }

    /**
     * Delete all references to question, dragItems and dropLocations if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    public void checkAndDeleteReferences(QuizExercise quizExercise) {

        // Delete all references to question, dropLocations and dragItem if the question was deleted
        if (!quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            mappings = null;
        }
        else {
            // find same quizQuestion in quizExercise
            QuizQuestion quizQuestion = quizExercise.findQuestionById(getQuizQuestion().getId());

            // Check if a dragItem or dropLocation was deleted and delete the mappings with it
            checkAndDeleteMappings((DragAndDropQuestion) quizQuestion);
        }
    }

    @Override
    public String toString() {
        return "DragAndDropSubmittedAnswer{" + "id=" + getId() + "}";
    }

    public Set<DnDMapping> toDnDMapping() {
        return getMappings().stream().map(mapping -> new DnDMapping(mapping.getDragItem().getId(), mapping.getDropLocation().getId())).collect(Collectors.toSet());
    }
}
