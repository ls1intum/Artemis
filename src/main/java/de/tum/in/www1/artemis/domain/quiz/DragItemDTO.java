package de.tum.in.www1.artemis.domain.quiz;

import java.io.Serializable;
import java.net.URI;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.Transient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A DragItem.
 */

public class DragItemDTO implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(DragItemDTO.class);

    @Transient
    private final transient FileService fileService = new FileService();

    private Long id;

    private String pictureFilePath;

    private String text;

    private Boolean invalid = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPictureFilePath() {
        return pictureFilePath;
    }

    public DragItemDTO pictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
        return this;
    }

    public void setPictureFilePath(String pictureFilePath) {
        this.pictureFilePath = pictureFilePath;
    }

    public String getText() {
        return text;
    }

    public DragItemDTO text(String text) {
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
