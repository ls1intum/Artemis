package de.tum.cit.aet.artemis.quiz.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.ServedFileUrl;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DragItemDTO(Long id, String pictureFilePath, String text, Boolean invalid) {

    /**
     * The client-facing view of a drag item, with its picture named by the path that serves it rather than by the filename that is stored.
     * <p>
     * The owning question has to supply its id: a drag item is not an entity of its own but a value inside {@code quiz_question.content}, its id is only unique within that
     * question, and it holds no reference back to it, so it cannot name the (question-scoped) URL of its own picture. That is also why the id is a parameter here rather than
     * something read off the item, and why this factory is the single place a drag item becomes client-facing JSON.
     *
     * @param questionId the id of the owning drag and drop question, or null while that question has not been inserted and therefore has no URL to hand out yet
     * @param dragItem   the drag item to project
     * @return the client-facing view of the drag item
     */
    public static DragItemDTO of(@Nullable Long questionId, DragItem dragItem) {
        return new DragItemDTO(dragItem.getId(), ServedFileUrl.dragItem(questionId, dragItem.getId(), dragItem.getPictureFilePath()), dragItem.getText(), dragItem.isInvalid());
    }

}
