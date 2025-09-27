package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemCreateDTO(@NotNull Long tempID, String text, String pictureFilePath) {

    /**
     * Converts this DTO to a {@link DragItem} domain object.
     * <p>
     * Maps the DTO properties directly to the corresponding fields in the domain object,
     * including temporary ID, text, and picture file path.
     *
     * @return the {@link DragItem} domain object with properties set from this DTO
     */
    public DragItem toDomainObject() {
        DragItem dragItem = new DragItem();
        dragItem.setTempID(tempID);
        dragItem.setText(text);
        dragItem.setPictureFilePath(pictureFilePath);
        return dragItem;
    }

    /**
     * Creates a {@link DragItemCreateDTO} from the given {@link DragItem} domain object.
     * <p>
     * Maps the domain object's properties to the corresponding DTO fields, including temporary ID,
     * text, and picture file path.
     *
     * @param dragItem the {@link DragItem} domain object to convert
     * @return the {@link DragItemCreateDTO} with properties set from the domain object
     */
    public static DragItemCreateDTO of(DragItem dragItem) {
        return new DragItemCreateDTO(dragItem.getTempID(), dragItem.getText(), dragItem.getPictureFilePath());
    }
}
