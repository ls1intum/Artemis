package de.tum.cit.aet.artemis.quiz.domain;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.FilePathParsingException;
import de.tum.cit.aet.artemis.domain.TempIdObject;
import de.tum.cit.aet.artemis.quiz.config.QuizView;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;

/**
 * A DragItem.
 */
@Entity
@Table(name = "drag_item")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion> {

    private static final Logger log = LoggerFactory.getLogger(DragItem.class);

    @Transient
    private final transient FileService fileService = new FileService();

    @Column(name = "picture_file_path")
    @JsonView(QuizView.Before.class)
    private String pictureFilePath;

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private DragAndDropQuestion question;

    // NOTE: without cascade and orphanRemoval, deletion of quizzes might not work properly, so we reference mappings here, even if we do not use them
    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "dragItem")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

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

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public Boolean isInvalid() {
        return invalid != null && invalid;
    }

    @Override
    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    /**
     * This method is called after the entity is saved for the first time. We replace the placeholder in the pictureFilePath with the id of the entity because we don't know it
     * before creation.
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    /**
     * This method is called when deleting this entity. It makes sure that the corresponding file is deleted as well.
     */
    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        try {
            if (pictureFilePath != null) {
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(pictureFilePath)), 0);
            }
        }
        catch (FilePathParsingException e) {
            // if the file path is invalid, we don't need to delete it
            log.warn("Could not delete file with path {}. Assume already deleted, DragAndDropQuestion {} can be removed.", pictureFilePath, getId());
        }
    }

    @Override
    public String toString() {
        return "DragItem{" + "id=" + getId() + ", pictureFilePath='" + getPictureFilePath() + "'" + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
