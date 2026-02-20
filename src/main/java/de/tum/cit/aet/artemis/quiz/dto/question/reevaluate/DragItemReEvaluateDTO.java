package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemReEvaluateDTO(@NotNull Long id, @NotNull Boolean invalid, String text, String pictureFilePath) {

    public static DragItemReEvaluateDTO of(DragItem dragItem) {
        return new DragItemReEvaluateDTO(dragItem.getId(), dragItem.isInvalid(), dragItem.getText(), dragItem.getPictureFilePath());
    }
}
