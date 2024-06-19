package de.tum.in.www1.artemis.domain.quiz;

import jakarta.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A DragItem.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion> {

    private static final Logger log = LoggerFactory.getLogger(DragItem.class);

    @Transient
    private final transient FileService fileService = new FileService();

    @JsonView(QuizView.Before.class)
    private String pictureFilePath;

    @JsonView(QuizView.Before.class)
    private String text;

    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @Override
    public void setQuestion(DragAndDropQuestion quizQuestion) {

    }

    public String getPictureFilePath() {
        return pictureFilePath;
    }

    public DragItem pictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
        return this;
    }

    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
    }

    public String getText() {
        return text;
    }

    public DragItem text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
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
