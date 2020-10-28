package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A Attachment.
 */
@Entity
@Table(name = "attachment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Attachment extends DomainObject implements Serializable {

    @Transient
    private transient FileService fileService = new FileService();

    @Transient
    private String prevLink;

    @Column(name = "name")
    private String name;

    @Column(name = "jhi_link")
    private String link;

    @Column(name = "version")
    private Integer version;

    @Column(name = "upload_date")
    private ZonedDateTime uploadDate;

    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type")
    private AttachmentType attachmentType;

    @ManyToOne
    @JsonIgnoreProperties("attachments")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("attachments")
    private Lecture lecture;

    @OneToOne
    @JoinColumn(name = "attachmentUnit_id")
    private AttachmentUnit attachmentUnit;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    /*
     * NOTE: The file management is necessary to differentiate between temporary and used files and to delete used files when the corresponding course is deleted or it is replaced
     * by another file. The workflow is as follows 1. user uploads a file -> this is a temporary file, because at this point the corresponding course might not exist yet. 2. user
     * saves the course -> now we move the temporary file which is addressed in courseIcon to a permanent location and update the value in courseIcon accordingly. => This happens
     * in @PrePersist and @PostPersist 3. user might upload another file to replace the existing file -> this new file is a temporary file at first 4. user saves changes (with the
     * new courseIcon pointing to the new temporary file) -> now we delete the old file in the permanent location and move the new file to a permanent location and update the value
     * in courseIcon accordingly. => This happens in @PreUpdate and uses @PostLoad to know the old path 5. When course is deleted, the file in the permanent location is deleted =>
     * This happens in @PostRemove
     */

    /**
     *Initialisation of the Attachment on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (attachmentType == AttachmentType.FILE && getLecture() != null && link != null && link.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            link = link.replace(Constants.FILEPATH_ID_PLACHEOLDER, getLecture().getId().toString());
        }
        if (attachmentType == AttachmentType.FILE && getAttachmentUnit() != null && link != null && link.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            link = link.replace(Constants.FILEPATH_ID_PLACHEOLDER, getAttachmentUnit().getId().toString());
        }

        prevLink = link; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    /**
     * Will be called before the entity is persisted (saved).
     * Manages files by taking care of file system changes for this entity.
     */
    @PrePersist
    public void beforeCreate() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // move file if necessary (id at this point will be null, so placeholder will be inserted)
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, FilePathService.getLectureAttachmentFilepath() + getLecture().getId() + '/', getLecture().getId(),
                    true);
        }
        if (attachmentType == AttachmentType.FILE && getAttachmentUnit() != null) {
            // move file if necessary (id at this point will be null, so placeholder will be inserted)
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, FilePathService.getAttachmentUnitFilePath() + getAttachmentUnit().getId() + '/',
                    getAttachmentUnit().getId(), true);
        }
    }

    /**
     * Will be called after the entity is persisted (saved).
     * Manages files by taking care of file system changes for this entity.
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (attachmentType == AttachmentType.FILE && link != null && link.contains(Constants.FILEPATH_ID_PLACHEOLDER) && getLecture() != null) {
            link = link.replace(Constants.FILEPATH_ID_PLACHEOLDER, getLecture().getId().toString());
        }
        if (attachmentType == AttachmentType.FILE && link != null && link.contains(Constants.FILEPATH_ID_PLACHEOLDER) && getAttachmentUnit() != null) {
            link = link.replace(Constants.FILEPATH_ID_PLACHEOLDER, getAttachmentUnit().getId().toString());
        }
    }

    /**
     * Will be called before the entity is flushed.
     * Manages files by taking care of file system changes for this entity.
     */
    @PreUpdate
    public void onUpdate() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // move file and delete old file if necessary
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, FilePathService.getLectureAttachmentFilepath() + getLecture().getId() + '/', getLecture().getId(),
                    true);
        }
        if (attachmentType == AttachmentType.FILE && getAttachmentUnit() != null) {
            // move file and delete old file if necessary
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, FilePathService.getAttachmentUnitFilePath() + getAttachmentUnit().getId() + '/',
                    getAttachmentUnit().getId(), true);
        }
    }

    /**
     * Will be called after the entity is removed (deleted).
     * Manages files by taking care of file system changes for this entity.
     */
    @PostRemove
    public void onDelete() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // delete old file if necessary
            fileService.manageFilesForUpdatedFilePath(prevLink, null, FilePathService.getLectureAttachmentFilepath() + getLecture().getId() + '/', getLecture().getId(), true);
        }
        if (attachmentType == AttachmentType.FILE && getAttachmentUnit() != null) {
            // delete old file if necessary
            fileService.manageFilesForUpdatedFilePath(prevLink, null, FilePathService.getAttachmentUnitFilePath() + getAttachmentUnit().getId() + '/', getAttachmentUnit().getId(),
                    true);
        }
    }

    public String getName() {
        return name;
    }

    public Attachment name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLink() {
        return link;
    }

    public Attachment link(String link) {
        this.link = link;
        return this;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public ZonedDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(ZonedDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public AttachmentType getAttachmentType() {
        return attachmentType;
    }

    public Attachment attachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
        return this;
    }

    public void setAttachmentType(AttachmentType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public AttachmentUnit getAttachmentUnit() {
        return attachmentUnit;
    }

    public void setAttachmentUnit(AttachmentUnit attachmentUnit) {
        this.attachmentUnit = attachmentUnit;
    }

    public Boolean isVisibleToStudents() {
        if (releaseDate == null) {  // no release date means the attachment is visible to students
            return Boolean.TRUE;
        }
        return releaseDate.isBefore(ZonedDateTime.now());
    }

    @Override
    public String toString() {
        return "Attachment{" + "id=" + getId() + ", name='" + getName() + "'" + ", link='" + getLink() + "'" + ", version='" + getVersion() + "'" + ", uploadDate='"
                + getUploadDate() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", attachmentType='" + getAttachmentType() + "'" + "}";
    }
}
