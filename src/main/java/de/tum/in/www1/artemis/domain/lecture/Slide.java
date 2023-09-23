package de.tum.in.www1.artemis.domain.lecture;

import java.nio.file.Path;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PostLoad;
import javax.persistence.PostRemove;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.service.EntityFileService;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

@Entity
@Table(name = "slide")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

    @Transient
    private final transient FilePathService filePathService = new FilePathService();

    @Transient
    private final transient FileService fileService = new FileService();

    @Transient
    private final transient EntityFileService entityFileService = new EntityFileService(fileService, filePathService);

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

    /**
     * Before persisting the slide, we need to move the file from the temp folder to the actual folder
     */
    @PrePersist
    public void beforeCreate() {
        if (slideImagePath == null) {
            return;
        }
        slideImagePath = entityFileService.moveFileBeforeEntityPersistenceWithIdIfIsTemp(slideImagePath, getTargetFolder(), false, (long) getSlideNumber());
    }

    @PreUpdate
    public void onUpdate() {
        slideImagePath = entityFileService.handlePotentialFileUpdateBeforeEntityPersistence((long) getSlideNumber(), prevSlideImagePath, slideImagePath, getTargetFolder(), false);
    }

    @PostRemove
    public void onDelete() {
        if (prevSlideImagePath != null) {
            fileService.schedulePathForDeletion(Path.of(prevSlideImagePath), 0);
        }
    }

    private Path getTargetFolder() {
        return FilePathService.getAttachmentUnitFilePath().resolve(Path.of(getAttachmentUnit().getId().toString(), "slide", String.valueOf(getSlideNumber())));
    }
}
