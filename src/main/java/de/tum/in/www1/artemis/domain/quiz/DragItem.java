package de.tum.in.www1.artemis.domain.quiz;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.domain.view.QuizView;
import de.tum.in.www1.artemis.service.EntityFileService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A DragItem.
 */
@Entity
@Table(name = "drag_item")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends TempIdObject implements QuizQuestionComponent<DragAndDropQuestion> {

    @Transient
    private final transient FilePathService filePathService = new FilePathService();

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private final transient EntityFileService entityFileService = new EntityFileService(fileService, filePathService);

    @Transient
    private String prevPictureFilePath;

    @Column(name = "picture_file_path")
    @JsonView(QuizView.Before.class)
    private String pictureFilePath;

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid = false;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

    @OneToMany(cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "dragItem")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

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

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public DragItem addMappings(DragAndDropMapping mapping) {
        this.mappings.add(mapping);
        mapping.setDragItem(this);
        return this;
    }

    public DragItem removeMappings(DragAndDropMapping mapping) {
        this.mappings.remove(mapping);
        mapping.setDragItem(null);
        return this;
    }

    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding drag item is deleted or it is
     * replaced by another file. The workflow is as follows 1. user uploads a file -> this is a temporary file, because at this point the corresponding drag item might not exist
     * yet. 2. user saves the drag item -> now we move the temporary file which is addressed in pictureFilePath to a permanent location and update the value in pictureFilePath
     * accordingly. => This happens in @PrePersist and @PostPersist 3. user might upload another file to replace the existing file -> this new file is a temporary file at first 4.
     * user saves changes (with the new pictureFilePath pointing to the new temporary file) -> now we delete the old file in the permanent location and move the new file to a
     * permanent location and update the value in pictureFilePath accordingly. => This happens in @PreUpdate and uses @PostLoad to know the old path 5. When drag item is deleted,
     * the file in the permanent location is deleted => This happens in @PostRemove NOTE: Number 3 and 4 are not possible for drag items with the current UI, but might be possible
     * in the future and are implemented here to prevent unexpected behaviour when UI changes and to keep code similar to DragAndDropQuestion.java
     */

    /**
     * Initialisation of the DragItem on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
        // save current path as old path (needed to know old path in onUpdate() and onDelete())
        prevPictureFilePath = pictureFilePath;
    }

    @PrePersist
    public void beforeCreate() {
        if (pictureFilePath != null) {
            pictureFilePath = entityFileService.moveTempFileBeforeEntityPersistence(pictureFilePath, FilePathService.getDragItemFilePath(), false);
        }
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        pictureFilePath = entityFileService.handlePotentialFileUpdateBeforeEntityPersistence(getId(), prevPictureFilePath, pictureFilePath, FilePathService.getDragItemFilePath(),
                false);
    }

    @PostRemove
    public void onDelete() {
        if (prevPictureFilePath != null) {
            fileService.schedulePathForDeletion(Path.of(prevPictureFilePath), 0);
        }
    }

    @Override
    public String toString() {
        return "DragItem{" + "id=" + getId() + ", pictureFilePath='" + getPictureFilePath() + "'" + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
