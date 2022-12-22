package de.tum.in.www1.artemis.web.rest.dto;

import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;

public class LectureUnitsDTO {

    private AttachmentUnit attachmentUnit;

    private Attachment attachment;

    private MultipartFile file;

    public LectureUnitsDTO() {
    }

    public LectureUnitsDTO(AttachmentUnit attachmentUnit, Attachment attachment, MultipartFile file) {
        this.attachmentUnit = attachmentUnit;
        this.attachment = attachment;
        this.file = file;
    }

    public AttachmentUnit getAttachmentUnit() {
        return attachmentUnit;
    }

    public void setAttachmentUnit(AttachmentUnit attachmentUnit) {
        this.attachmentUnit = attachmentUnit;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}
