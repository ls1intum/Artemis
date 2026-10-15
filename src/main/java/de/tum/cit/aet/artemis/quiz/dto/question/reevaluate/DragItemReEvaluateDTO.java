package de.tum.cit.aet.artemis.quiz.dto.question.reevaluate;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_DRAG_ITEM_TEXT_LENGTH;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemReEvaluateDTO(@NotNull Long id, @NotNull Boolean invalid, @Size(max = MAX_QUIZ_DRAG_ITEM_TEXT_LENGTH) String text, String pictureFilePath) {

    public static DragItemReEvaluateDTO of(DragItem dragItem) {
        return new DragItemReEvaluateDTO(dragItem.getId(), dragItem.isInvalid(), dragItem.getText(), dragItem.getPictureFilePath());
    }
}
