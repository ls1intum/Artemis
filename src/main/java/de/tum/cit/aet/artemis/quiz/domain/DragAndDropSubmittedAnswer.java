package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.quiz.domain.compare.DnDMapping;

/**
 * A DragAndDropSubmittedAnswer.
 * <p>
 * The submitted drag-item/drop-location pairs are stored inside the {@code submitted_answer.selection} JSON column (as {@link DragAndDropMappingSelection} entries in a
 * {@link DragAndDropSubmittedAnswerSelection}) instead of the former {@code @OneToMany} {@code drag_and_drop_mapping} rows. The public {@code getMappings()} /
 * {@code setMappings()}
 * accessors keep their original signatures and shape: they resolve the stored scalar ids against the owning question so the REST/websocket wire format (nested {@code dragItem} /
 * {@code dropLocation} objects) is preserved for callers, DTOs and the raw exam conduction/submit endpoints.
 */
@Entity
@DiscriminatorValue(value = "DD")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragAndDropSubmittedAnswer extends SubmittedAnswer {

    private DragAndDropSubmittedAnswerSelection dndSelection() {
        if (getSelection() instanceof DragAndDropSubmittedAnswerSelection dragAndDropSelection) {
            return dragAndDropSelection;
        }
        DragAndDropSubmittedAnswerSelection created = new DragAndDropSubmittedAnswerSelection();
        setSelection(created);
        return created;
    }

    private DragAndDropQuestion dragAndDropQuestion() {
        return getQuizQuestion() instanceof DragAndDropQuestion question ? question : null;
    }

    /**
     * The submitted mappings resolved into object-based {@link DragAndDropMapping}s (with their drag item / drop location objects) against the owning question. Built on demand
     * from
     * the scalar-id selection stored in the JSON column. Mutating the returned set does not affect the stored selection — use {@link #addMappings} / {@link #removeMappings} /
     * {@link #setMappings} instead.
     *
     * @return the resolved submitted mappings
     */
    public Set<DragAndDropMapping> getMappings() {
        Set<DragAndDropMapping> result = new HashSet<>();
        if (!(getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
            return result;
        }
        DragAndDropQuestion question = dragAndDropQuestion();
        if (question == null) {
            return result;
        }
        for (DragAndDropMappingSelection entry : selection.getMappings()) {
            DragItem dragItem = question.findDragItemById(entry.dragItemId());
            DropLocation dropLocation = question.findDropLocationById(entry.dropLocationId());
            // Skip mappings whose drag item or drop location no longer exists on the question (e.g. removed during re-evaluation before checkAndDeleteReferences ran, or an
            // orphaned selection): consumers resolve the nested objects unconditionally, so a null drag item/drop location would NPE. Mirrors getCorrectMappings() and the
            // MultipleChoiceSubmittedAnswer.getSelectedOptions() null-skip.
            if (dragItem == null || dropLocation == null) {
                continue;
            }
            DragAndDropMapping mapping = new DragAndDropMapping();
            mapping.setDragItem(dragItem);
            mapping.setDropLocation(dropLocation);
            int dragItemIndex = question.getDragItems().indexOf(dragItem);
            mapping.setDragItemIndex(dragItemIndex >= 0 ? dragItemIndex : null);
            int dropLocationIndex = question.getDropLocations().indexOf(dropLocation);
            mapping.setDropLocationIndex(dropLocationIndex >= 0 ? dropLocationIndex : null);
            result.add(mapping);
        }
        return result;
    }

    /**
     * Replaces the submitted mappings, storing them as scalar id pairs in the JSON selection.
     *
     * @param mappings the object-based submitted mappings whose drag item / drop location ids are stored
     */
    public void setMappings(Set<DragAndDropMapping> mappings) {
        List<DragAndDropMappingSelection> entries = new ArrayList<>();
        if (mappings != null) {
            for (DragAndDropMapping mapping : mappings) {
                entries.add(new DragAndDropMappingSelection(idOf(mapping.getDragItem()), idOf(mapping.getDropLocation())));
            }
        }
        dndSelection().setMappings(entries);
    }

    public DragAndDropSubmittedAnswer addMappings(DragAndDropMapping mapping) {
        dndSelection().getMappings().add(new DragAndDropMappingSelection(idOf(mapping.getDragItem()), idOf(mapping.getDropLocation())));
        return this;
    }

    public DragAndDropSubmittedAnswer removeMappings(DragAndDropMapping mapping) {
        Long dragItemId = idOf(mapping.getDragItem());
        Long dropLocationId = idOf(mapping.getDropLocation());
        dndSelection().getMappings().removeIf(entry -> Objects.equals(entry.dragItemId(), dragItemId) && Objects.equals(entry.dropLocationId(), dropLocationId));
        return this;
    }

    private static Long idOf(DomainObject component) {
        return component != null ? component.getId() : null;
    }

    /**
     * Get the drag item that was drag-and-dropped on the given drop location
     *
     * @param dropLocation the drop location
     * @return the selected drag item for the given drop location (may be null if no drag item was dropped on this drop location)
     */
    public DragItem getSelectedDragItemForDropLocation(DropLocation dropLocation) {
        if (dropLocation == null || dropLocation.getId() == null || !(getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
            return null;
        }
        DragAndDropQuestion question = dragAndDropQuestion();
        if (question == null) {
            return null;
        }
        for (DragAndDropMappingSelection entry : selection.getMappings()) {
            if (dropLocation.getId().equals(entry.dropLocationId())) {
                return question.findDragItemById(entry.dragItemId());
            }
        }
        return null;
    }

    /**
     * Delete all references to quizQuestion, dragItems and dropLocations if the quiz was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        // Delete all references if the question was deleted
        if (getQuizQuestion() == null || !quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            setSelection(null);
            return;
        }
        // Check if a dragItem or dropLocation was deleted and remove the affected submitted mappings
        if (quizExercise.findQuestionById(getQuizQuestion().getId()) instanceof DragAndDropQuestion question
                && getSelection() instanceof DragAndDropSubmittedAnswerSelection selection) {
            Set<Long> dragItemIds = question.getDragItems().stream().map(DragItem::getId).collect(Collectors.toSet());
            Set<Long> dropLocationIds = question.getDropLocations().stream().map(DropLocation::getId).collect(Collectors.toSet());
            selection.getMappings().removeIf(entry -> !dragItemIds.contains(entry.dragItemId()) || !dropLocationIds.contains(entry.dropLocationId()));
        }
    }

    @Override
    public String toString() {
        return "DragAndDropSubmittedAnswer{" + "id=" + getId() + "}";
    }

    /**
     * @return the submitted mappings as a set of id-based {@link DnDMapping} value objects, used to compare submissions for content equality.
     */
    public Set<DnDMapping> toDnDMapping() {
        if (!(getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
            return new HashSet<>();
        }
        return selection.getMappings().stream().filter(entry -> entry.dragItemId() != null && entry.dropLocationId() != null)
                .map(entry -> new DnDMapping(entry.dragItemId(), entry.dropLocationId())).collect(Collectors.toSet());
    }
}
