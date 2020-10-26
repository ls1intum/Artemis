package de.tum.in.www1.artemis.domain.lecture_unit;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.Attachment;

@Entity
@DiscriminatorValue("A")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class AttachmentUnit extends LectureUnit {

    @Column(name = "description")
    @Lob
    private String description;

    @OneToOne(mappedBy = "attachmentUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = "attachmentUnit")
    private Attachment attachment;

    @Override
    public String getName() {
        if (this.attachment != null && this.attachment.getName() != null) {
            return this.attachment.getName();
        }
        else {
            return null;
        }
    }

    @Override
    public void setName(String name) {
        // Do nothing as the name will always be taken from the attachment
    }

    @Override
    public ZonedDateTime getReleaseDate() {
        if (this.attachment != null && this.attachment.getReleaseDate() != null) {
            return this.attachment.getReleaseDate();
        }
        else {
            return null;
        }
    }

    @Override
    public boolean calculateVisibility() {
        if (attachment == null) {
            return true;
        }
        else {
            return attachment.isVisibleToStudents();
        }
    }

    @Override
    public void setReleaseDate(ZonedDateTime releaseDate) {
        // Do nothing as the release date will always be taken from the attachment
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
