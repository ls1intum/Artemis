package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    @OneToMany(mappedBy = "attachmentUnit", fetch = FetchType.EAGER)
    @JsonIgnoreProperties("attachmentUnit")
    @OrderBy("slideNumber ASC")
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

    /**
     * Returns a filtered list of slides where only the latest version of each slide (based on slide number) is included.
     * For slides with the same slide number, only the one with the highest ID is kept.
     *
     * @return A list of the most recent version of each slide, ordered by slide number
     */
    public List<Slide> getSlides() {
        // A map to keep only the slide with the highest ID for each slide number
        Map<Integer, Slide> latestSlideByNumber = new HashMap<>();

        for (Slide slide : slides) {
            int slideNumber = slide.getSlideNumber();
            if (!latestSlideByNumber.containsKey(slideNumber) || slide.getId() > latestSlideByNumber.get(slideNumber).getId()) {
                latestSlideByNumber.put(slideNumber, slide);
            }
        }

        return new ArrayList<>(latestSlideByNumber.values());
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
