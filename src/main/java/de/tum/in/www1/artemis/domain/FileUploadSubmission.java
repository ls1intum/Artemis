package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A FileUploadSubmission.
 */
@Entity
@DiscriminatorValue(value = "F")
public class FileUploadSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private FileService fileService = new FileService();

    @Transient
    private String prevFilePath;

    @Column(name = "file_path")
    private String filePath;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding course is deleted or it is replaced
     * by another file. The workflow is as follows 1. user uploads a file -> this is a temporary file, because at this point the corresponding submission might not exist yet. 2.
     * user submits the submission -> now we move the temporary file which to a permanent location. => This happens in @PrePersist and @PostPersist 3. When course is deleted, the
     * file in the permanent location is deleted => This happens in @PostRemove
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (getFilePath() != null && getFilePath().contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            filePath = filePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
        // save current path as old path (needed to know old path in onUpdate() and onDelete())
        prevFilePath = filePath;
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        filePath = fileService.manageFilesForUpdatedFilePath(prevFilePath, filePath, Constants.FILE_UPLOAD_SUBMISSION_FILEPATH + getId() + '/', getId(), true);
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (getFilePath() != null && getFilePath().contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            filePath = filePath.replace(Constants.FILEPATH_ID_PLACHEOLDER, getId().toString());
        }
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        fileService.manageFilesForUpdatedFilePath(prevFilePath, null, Constants.FILE_UPLOAD_SUBMISSION_FILEPATH + getId() + '/', getId(), true);
    }

    public String getFilePath() {
        return filePath;
    }

    public FileUploadSubmission filePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileUploadSubmission fileUploadSubmission = (FileUploadSubmission) o;
        if (fileUploadSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), fileUploadSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "FileUploadSubmission{" + "id=" + getId() + ", filePath='" + getFilePath() + "'" + "}";
    }
}
