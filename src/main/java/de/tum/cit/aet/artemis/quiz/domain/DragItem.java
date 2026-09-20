package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;

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
 * The {@code pictureFilePath} holds nothing but the filename, and this class hands out nothing else. It is the one served file reference whose URL its own entity cannot build:
 * the URL is {@code drag-and-drop/questions/{questionId}/drag-items/{dragItemId}/{filename}} because a drag item id is only unique within its question, and a drag item carries no
 * reference back to that question. The owning question supplies its id at the projection boundary instead, in {@code DragItemDTO#of}, which every client-facing shape of a drag
 * item goes through.
 * <p>
 * <b>The served path must not become a Jackson property of this class</b>, the way {@code DragAndDropQuestion} exposes one for its background image. A background image is a
 * column, so its Jackson form is client-facing only; a drag item <i>is</i> the persisted form, because Hibernate writes {@code quiz_question.content} with Jackson. A derived
 * property here would therefore be written into the column, and it would also make the value depend on state the load-time snapshot Hibernate dirty-checks against does not carry,
 * which is how a mere read of a question turns into a rewrite of its row (see {@link QuizQuestionContent#haveEqualPersistedForm}).
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

    /**
     * @return the stored filename of the drag item picture
     */
    public String getPictureFilePath() {
        return pictureFilePath;
    }

    /**
     * Stores the filename of the given value. See {@link FileSystemLocation#storedFilename} for why a served URL sent back by a client cannot end up in the stored content.
     *
     * @param pictureFilePath the filename of the picture, or the URL it is served under
     */
    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = FileSystemLocation.storedFilename(pictureFilePath);
    }

    /**
     * @param pictureFilePath the filename of the picture, or the URL it is served under
     * @return this drag item for fluent chaining
     */
    public DragItem pictureFilePath(String pictureFilePath) {
        setPictureFilePath(pictureFilePath);
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
