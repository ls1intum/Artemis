package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single drag-and-drop pair a student submitted: the drag item with id {@link #dragItemId} was dropped onto the drop location with id {@link #dropLocationId}.
 * <p>
 * This is the submission-side counterpart to the question-owned {@link DragAndDropMapping}. Unlike the question's correct mapping it has no identity of its own — a student's
 * mapping
 * simply exists or does not, and duplicates are meaningless. It is stored inside {@link DragAndDropSubmittedAnswerSelection} in the {@code submitted_answer.selection} JSON column.
 *
 * @param dragItemId     the question-scoped id of the dragged item
 * @param dropLocationId the question-scoped id of the drop location it was dropped onto
 */
public record DragAndDropMappingSelection(@JsonProperty("dragItemId") Long dragItemId, @JsonProperty("dropLocationId") Long dropLocationId) {
}
