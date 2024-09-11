package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Attachment;

@Entity
@DiscriminatorValue("A")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AttachmentUnit extends LectureUnit {

    // Note: Name and Release Date will always be taken from associated attachment
    @Column(name = "description")
    private String description;

    @OneToOne(mappedBy = "attachmentUnit", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "attachmentUnit", allowSetters = true)
    private Attachment attachment;

    @OneToMany(mappedBy = "attachmentUnit", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("attachmentUnit")
    private List<Slide> slides = new ArrayList<>();

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

    public List<Slide> getSlides() {
        return slides;
    }

    public void setSlides(List<Slide> slides) {
        this.slides = slides;
    }

    @Override
    public String getName() {
        return attachment == null ? null : attachment.getName();
    }

    @Override
    public void setName(String name) {
        // Should be set in associated attachment
    }

    @Override
    public ZonedDateTime getReleaseDate() {
        return attachment == null ? null : attachment.getReleaseDate();
    }

    @Override
    public void setReleaseDate(ZonedDateTime releaseDate) {
        // Should be set in associated attachment
    }

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "attachment";
    }
}
