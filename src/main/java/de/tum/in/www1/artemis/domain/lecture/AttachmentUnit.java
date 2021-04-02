package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.*;
import de.tum.in.www1.artemis.domain.Attachment;

@Entity
@DiscriminatorValue("A")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AttachmentUnit extends LectureUnit {

    // Note: Name and Release Date will always be taken from associated attachment
    @Column(name = "description")
    @Lob
    private String description;

    @OneToOne(mappedBy = "attachmentUnit", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "attachmentUnit", allowSetters = true)
    private Attachment attachment;

    @Override
    public boolean isVisibleToStudents() {
        if (attachment == null) {
            return true;
        }
        else {
            return attachment.isVisibleToStudents();
        }
    }

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
