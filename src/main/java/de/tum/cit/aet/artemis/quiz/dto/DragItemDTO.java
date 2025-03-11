package de.tum.cit.aet.artemis.quiz.dto;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

public record DragItemDTO(Long id, String pictureFilePath, String text, Boolean invalid) {

    public static DragItemDTO of(DragItem dragItem) {
        return new DragItemDTO(dragItem.getId(), dragItem.getPictureFilePath(), dragItem.getText(), dragItem.isInvalid());
    }

}
