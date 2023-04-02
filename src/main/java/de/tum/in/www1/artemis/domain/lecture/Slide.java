package de.tum.in.www1.artemis.domain.lecture;

import java.nio.file.Path;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

@Entity
@Table(name = "slide")
@DiscriminatorValue("S")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private String prevSlideImagePath;

    @ManyToOne
    @JoinColumn(name = "attachment_unit_id")
    private AttachmentUnit attachmentUnit;

    @Size(max = 150)
    @Column(name = "slide_image_path", length = 150)
    private String slideImagePath;

    @Column(name = "slide_number")
    private int slideNumber;

    public AttachmentUnit getAttachmentUnit() {
        return attachmentUnit;
    }

    public void setAttachmentUnit(AttachmentUnit attachmentUnit) {
        this.attachmentUnit = attachmentUnit;
    }

    public String getSlideImagePath() {
        return slideImagePath;
    }

    public void setSlideImagePath(String slideImagePath) {
        this.slideImagePath = slideImagePath;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    /**
     * Initialisation of the Slide on Server start
     */
    @PostLoad
    public void onLoad() {
        // replace placeholder with actual id if necessary (this is needed because changes made in afterCreate() are not persisted)
        if (slideImagePath != null && slideImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            slideImagePath = slideImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getAttachmentUnit().getId().toString());
        }
        prevSlideImagePath = slideImagePath; // save current path as old path (needed to know old path in onUpdate() and onDelete())
    }

    @PrePersist
    public void beforeCreate() {
        // move file if necessary (id at this point will be null, so placeholder will be inserted)
        var targetFolder = Path.of(FilePathService.getSlideImageFilePath(), getAttachmentUnit().getId().toString()).toString();
        slideImagePath = fileService.manageFilesForUpdatedFilePath(prevSlideImagePath, slideImagePath, targetFolder, getAttachmentUnit().getId());
    }

    /**
     * Will be called after the entity is persisted (saved).
     * Manages files by taking care of file system changes for this entity.
     */
    @PostPersist
    public void afterCreate() {
        // replace placeholder with actual id if necessary (id is no longer null at this point)
        if (slideImagePath != null && slideImagePath.contains(Constants.FILEPATH_ID_PLACEHOLDER)) {
            slideImagePath = slideImagePath.replace(Constants.FILEPATH_ID_PLACEHOLDER, getAttachmentUnit().getId().toString());
        }
    }

    @PreUpdate
    public void onUpdate() {
        // move file and delete old file if necessary
        var targetFolder = Path.of(FilePathService.getSlideImageFilePath(), getAttachmentUnit().getId().toString()).toString();
        slideImagePath = fileService.manageFilesForUpdatedFilePath(prevSlideImagePath, slideImagePath, targetFolder, getAttachmentUnit().getId());
    }

    @PostRemove
    public void onDelete() {
        // delete old file if necessary
        var targetFolder = Path.of(FilePathService.getSlideImageFilePath(), getAttachmentUnit().getId().toString()).toString();
        fileService.manageFilesForUpdatedFilePath(prevSlideImagePath, null, targetFolder, getAttachmentUnit().getId());
    }
}
