package de.tum.cit.aet.artemis.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemDTO(Long id, String pictureFilePath, String text, Boolean invalid) {

    public static DragItemDTO of(DragItem dragItem) {
        return new DragItemDTO(dragItem.getId(), dragItem.getPictureFilePath(), dragItem.getText(), dragItem.isInvalid());
    }

}
