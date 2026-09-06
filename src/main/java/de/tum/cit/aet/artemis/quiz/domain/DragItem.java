package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A DragItem of a {@link DragAndDropQuestion}.
 * <p>
 * Formerly a JPA entity backed by the {@code drag_item} table; it is now a plain POJO stored inside the question's {@code content} JSON column (see
 * {@link DragAndDropQuestionContent}). It no longer holds a back-reference to the question or its mappings.
 * <p>
 * The former {@code @PostPersist}/{@code @PostRemove} file-lifecycle callbacks are gone: because the {@code id} is now minted in Java before the owning question is saved, the
 * {@code pictureFilePath} is written with the real (question-scoped) id directly, and file deletion on question/exercise removal is orchestrated explicitly by the service layer
 * (see {@code QuizExerciseService}).
 * <p>
 * The {@code pictureFilePath} is stored as the URL that serves it, {@code drag-and-drop/questions/{questionId}/drag-items/{dragItemId}/{filename}}, so a client can append it to
 * {@code api/core/files} unchanged. Only the question id is unknown while the question is being created; a placeholder is written for it and
 * {@code DragAndDropQuestion.afterCreate()} fills it in.
 * <p>
 * It still extends {@link DomainObject} to reuse the {@code id} field and its id-based {@code equals}/{@code hashCode}; the inherited JPA annotations are inert because this class
 * is
 * no longer an {@code @Entity}. The {@code id} is question-scoped (unique within the owning question, not globally).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends DomainObject {

    private String pictureFilePath;

    private String text;

    private Boolean invalid = false;

    public String getPictureFilePath() {
        return pictureFilePath;
    }

    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
    }

    public DragItem pictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
        return this;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public DragItem text(String text) {
        this.text = text;
        return this;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    @Override
    public String toString() {
        return "DragItem{" + "id=" + getId() + ", pictureFilePath='" + getPictureFilePath() + "'" + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
