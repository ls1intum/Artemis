package de.tum.in.www1.artemis.domain.lecture;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Attachment;

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

    @OneToMany(mappedBy = "lectureUnit", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore // important, so that the completion status of other users do not leak to anyone
    private Set<LectureUnitCompletion> completedUsers = new HashSet<>();

    @OneToMany(mappedBy = "attachmentUnit", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("attachmentUnit")
    private Set<Slide> slides = new HashSet<>();

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

    public Set<Slide> getSlides() {
        return slides;
    }

    public void setSlides(Set<Slide> slides) {
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
}
