package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemCreateDTO(long tempID, String text, String pictureFilePath) {

    public DragItem toDomainObject() {
        DragItem dragItem = new DragItem();
        dragItem.setTempID(tempID);
        dragItem.setText(text);
        dragItem.setPictureFilePath(pictureFilePath);
        return dragItem;
    }
}
