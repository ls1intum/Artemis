package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "slide")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Slide extends DomainObject {

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
}
