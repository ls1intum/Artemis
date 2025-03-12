package de.tum.cit.aet.artemis.lecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("V")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VideoUnit extends LectureUnit {

    @Column(name = "description")
    private String description;

    @Column(name = "source")
    private String source;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @OneToOne
    @JoinColumn(name = "corresponding_attachment_unit_id")
    @JsonIgnoreProperties({ "correspondingVideoUnit", "lecture" })
    private AttachmentUnit correspondingAttachmentUnit;

    public AttachmentUnit getCorrespondingAttachmentUnit() {
        return correspondingAttachmentUnit;
    }

    public void setCorrespondingAttachmentUnit(AttachmentUnit attachmentUnit) {
        this.correspondingAttachmentUnit = attachmentUnit;
    }

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "video";
    }
}
