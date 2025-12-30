package de.tum.cit.aet.artemis.quiz.dto.question.fromEditor;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

/**
 * DTO for drag items in the editor context.
 * Supports both creating new items (id is null) and updating existing items (id is non-null).
 *
 * @param id              the ID of the drag item, null for new items
 * @param tempID          the temporary ID for matching during creation (can be null for persisted entities, will use id instead)
 * @param text            the text of the drag item (for text-based items)
 * @param pictureFilePath the picture file path (for image-based items)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemFromEditorDTO(Long id, Long tempID, String text, String pictureFilePath) {

    /**
     * Creates a DragItemFromEditorDTO from the given DragItem domain object.
     * For persisted entities, uses the id as tempID if tempID is null.
     *
     * @param dragItem the drag item to convert
     * @return the corresponding DTO
     */
    public static DragItemFromEditorDTO of(DragItem dragItem) {
        // Use id as tempID fallback for persisted entities
        Long effectiveTempID = dragItem.getTempID() != null ? dragItem.getTempID() : dragItem.getId();
        return new DragItemFromEditorDTO(dragItem.getId(), effectiveTempID, dragItem.getText(), dragItem.getPictureFilePath());
    }

    /**
     * Creates a new DragItem domain object from this DTO.
     *
     * @return a new DragItem domain object
     */
    public DragItem toDomainObject() {
        DragItem dragItem = new DragItem();
        // Use id as tempID fallback for mapping resolution
        dragItem.setTempID(tempID != null ? tempID : id);
        dragItem.setText(text);
        dragItem.setPictureFilePath(pictureFilePath);
        return dragItem;
    }

    /**
     * Applies the DTO values to an existing DragItem entity.
     *
     * @param dragItem the existing drag item to update
     */
    public void applyTo(DragItem dragItem) {
        dragItem.setTempID(tempID != null ? tempID : id);
        dragItem.setText(text);
        dragItem.setPictureFilePath(pictureFilePath);
    }

    /**
     * Gets the effective ID used for mapping resolution (tempID if available, otherwise id).
     *
     * @return the effective ID for matching
     */
    public Long effectiveId() {
        return tempID != null ? tempID : id;
    }
}
