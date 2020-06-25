package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.service.FileService;

/**
 * A Attachment.
 */
@Entity
@Table(name = "attachment")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Attachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Transient
    private transient FileService fileService = new FileService();

    @Transient
    private String prevLink;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        prevLink = link; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    @PrePersist
    public void beforeCreate() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // move file if necessary (id at this point will be null, so placeholder will be inserted)
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, Constants.LECTURE_ATTACHMENT_FILEPATH + getLecture().getId() + '/', getLecture().getId(), true);
        }
    }

    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (attachmentType == AttachmentType.FILE && link != null && link.contains(Constants.FILEPATH_ID_PLACHEOLDER)) {
            link = link.replace(Constants.FILEPATH_ID_PLACHEOLDER, getLecture().getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // move file and delete old file if necessary
            link = fileService.manageFilesForUpdatedFilePath(prevLink, link, Constants.LECTURE_ATTACHMENT_FILEPATH + getLecture().getId() + '/', getLecture().getId(), true);
        }
    }

    @PostRemove
    public void onDelete() {
        if (attachmentType == AttachmentType.FILE && getLecture() != null) {
            // delete old file if necessary
            fileService.manageFilesForUpdatedFilePath(prevLink, null, Constants.LECTURE_ATTACHMENT_FILEPATH + getLecture().getId() + '/', getLecture().getId(), true);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Attachment version(Integer version) {
        this.version = version;
        return this;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public ZonedDateTime getUploadDate() {
        return uploadDate;
    }

    public Attachment uploadDate(ZonedDateTime uploadDate) {
        this.uploadDate = uploadDate;
        return this;
    }

    public void setUploadDate(ZonedDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public Attachment releaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
        return this;
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

    public Attachment exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public Attachment lecture(Lecture lecture) {
        this.lecture = lecture;
        return this;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
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
        Attachment attachment = (Attachment) o;
        if (attachment.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), attachment.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Attachment{" + "id=" + getId() + ", name='" + getName() + "'" + ", link='" + getLink() + "'" + ", version='" + getVersion() + "'" + ", uploadDate='"
                + getUploadDate() + "'" + ", releaseDate='" + getReleaseDate() + "'" + ", attachmentType='" + getAttachmentType() + "'" + "}";
    }
}
