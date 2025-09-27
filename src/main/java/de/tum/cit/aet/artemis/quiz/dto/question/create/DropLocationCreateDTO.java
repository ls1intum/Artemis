package de.tum.cit.aet.artemis.quiz.dto.question.create;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DropLocation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DropLocationCreateDTO(long tempID, double posX, double posY, double width, double height) {

    public DropLocation toDomainObject() {
        DropLocation dragItem = new DropLocation();
        dragItem.setTempID(tempID);
        dragItem.setPosX(posX);
        dragItem.setPosY(posY);
        dragItem.setWidth(width);
        dragItem.setHeight(height);
        return dragItem;
    }
}
