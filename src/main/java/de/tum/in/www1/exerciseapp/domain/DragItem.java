package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.in.www1.exerciseapp.config.Constants;
import de.tum.in.www1.exerciseapp.domain.util.FileManagement;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DragItem.
 */
@Entity
@Table(name = "drag_item")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class DragItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private String prevPictureFilePath;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "picture_file_path")
    private String pictureFilePath;

    @Column(name = "text")
    private String text;

    @ManyToOne
    @JsonIgnore
    private DragAndDropQuestion question;

    @OneToMany(cascade = CascadeType.REMOVE, fetch = FetchType.EAGER, orphanRemoval = true, mappedBy = "dragItem")
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<DragAndDropMapping> mappings = new HashSet<>();

    /**
     * tempID is needed to refer to drag items that have not been persisted yet
     * in the correctMappings of a question (so user can create mappings in the UI before saving new drag items)
     */
    @Transient
    // variable name must be different from Getter name,
    // so that Jackson ignores the @Transient annotation,
    // but Hibernate still respects it
    private Long tempIDTransient;

    public Long getTempID() {
        return tempIDTransient;
    }

    public void setTempID(Long tempID) {
        this.tempIDTransient = tempID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public DragAndDropQuestion getQuestion() {
        return question;
    }

    public DragItem question(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
        return this;
    }

    public void setQuestion(DragAndDropQuestion dragAndDropQuestion) {
        this.question = dragAndDropQuestion;
    }

    public Set<DragAndDropMapping> getMappings() {
        return mappings;
    }

    public DragItem mappings(Set<DragAndDropMapping> mappings) {
        this.mappings = mappings;
        return this;
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
     * NOTE:
     *
     * The file management is necessary to differentiate between temporary and used files
     * and to delete used files when the corresponding drag item is deleted or it is replaced by
     * another file.
     *
     * The workflow is as follows
     *
     * 1. user uploads a file -> this is a temporary file,
     *           because at this point the corresponding drag item
     *           might not exist yet.
     * 2. user saves the drag item -> now we move the temporary file
     *           which is addressed in pictureFilePath to a permanent
     *           location and update the value in pictureFilePath accordingly.
     *           => This happens in @PrePersist and @PostPersist
     * 3. user might upload another file to replace the existing file
     *           -> this new file is a temporary file at first
     * 4. user saves changes (with the new pictureFilePath pointing to the new temporary file)
     *           -> now we delete the old file in the permanent location
     *              and move the new file to a permanent location and update
     *              the value in pictureFilePath accordingly.
     *           => This happens in @PreUpdate and uses @PostLoad to know the old path
     * 5. When drag item is deleted, the file in the permanent location is deleted
     *           => This happens in @PostRemove
     *
     *
     * NOTE: Number 3 and 4 are not possible for drag items with the current UI, but might be possible in the future
     *       and are implemented here to prevent unexpected behaviour when UI changes and to keep code similar to DragAndDropQuestion.java
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
        // save current path as old path (needed to know old path in onUpdate() and onDelete())
        prevPictureFilePath = pictureFilePath;
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        pictureFilePath = FileManagement.manageFilesForUpdatedFilePath(prevPictureFilePath, pictureFilePath, Constants.DRAG_ITEM_FILEPATH, getId());
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (pictureFilePath != null && pictureFilePath.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            pictureFilePath = pictureFilePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        pictureFilePath = FileManagement.manageFilesForUpdatedFilePath(prevPictureFilePath, pictureFilePath, Constants.DRAG_ITEM_FILEPATH, getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        FileManagement.manageFilesForUpdatedFilePath(prevPictureFilePath, null, Constants.DRAG_ITEM_FILEPATH, getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DragItem dragItem = (DragItem) o;
        if (dragItem.getTempID() != null && getTempID() != null && Objects.equals(getTempID(), dragItem.getTempID())) {
            return true;
        }
        if (dragItem.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), dragItem.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "DragItem{" +
            "id=" + getId() +
            ", pictureFilePath='" + getPictureFilePath() + "'" +
            ", text='" + getText() + "'" +
            "}";
    }
}
