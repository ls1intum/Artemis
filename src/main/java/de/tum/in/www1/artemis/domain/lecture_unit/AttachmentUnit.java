package de.tum.in.www1.artemis.domain.lecture_unit;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.Attachment;

@Entity
@DiscriminatorValue("A")
public class AttachmentUnit extends LectureUnit {

    @Column(name = "description")
    @Lob
    private String description;

    @OneToOne
    @JoinColumn(name = "attachment_id")
    private Attachment attachment;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }
}
