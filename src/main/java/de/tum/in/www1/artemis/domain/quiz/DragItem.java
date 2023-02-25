package de.tum.in.www1.artemis.domain.quiz;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A DragItem.
 */
@Entity
@Table(name = "drag_item")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DragItem extends TempIdObject {

    @Transient
    private transient FileService fileService = new FileService();

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

    /**
     * This method is called after the entity is saved for the first time. Before creation, we don't know yet the id of the entity, so we use a placeholder in the
     * backgroundFilePath
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getId().toString());
        }
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        try {
            if (pictureFilePath != null) {
                fileService.deleteFiles(List.of(Path.of(fileService.actualPathForPublicPath(pictureFilePath))));
            }
        }
        catch (FilePathParsingException ignored) {
            // if the file path is invalid, we don't need to delete it
        }
    }

    @Override
    public String toString() {
        return "DragItem{" + "id=" + getId() + ", pictureFilePath='" + getPictureFilePath() + "'" + ", text='" + getText() + "'" + ", invalid='" + isInvalid() + "'" + "}";
    }
}
