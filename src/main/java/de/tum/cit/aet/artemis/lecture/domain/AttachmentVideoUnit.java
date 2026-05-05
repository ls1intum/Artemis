package de.tum.cit.aet.artemis.lecture.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("A")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AttachmentVideoUnit extends LectureUnit {

    // Note: Name and Release Date will always be taken from associated attachment
    @Column(name = "description")
    private String description;

    @Column(name = "source")
    private String videoSource;

    @OneToOne(mappedBy = "attachmentVideoUnit", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "attachmentVideoUnit", allowSetters = true)
    private Attachment attachment;

    @OneToMany(mappedBy = "attachmentVideoUnit", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("attachmentVideoUnit")
    @OrderBy("slideNumber ASC")
    private List<Slide> slides = new ArrayList<>();

    /**
     * Maps slide index (0-based position in slides list) to visible page number in the PDF.
     * Value -1 indicates no page number is visible on that slide.
     * Generated during Pyris ingestion to enable accurate video-slide synchronization.
     */
    @Convert(converter = SlidePageNumberMapConverter.class)
    @Column(name = "slide_page_number_map", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<Integer, Integer> slidePageNumberMap;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVideoSource() {
        return videoSource;
    }

    public void setVideoSource(String videoSource) {
        this.videoSource = videoSource;
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

    public Map<Integer, Integer> getSlidePageNumberMap() {
        return slidePageNumberMap;
    }

    public void setSlidePageNumberMap(Map<Integer, Integer> slidePageNumberMap) {
        this.slidePageNumberMap = slidePageNumberMap;
    }

    // IMPORTANT NOTICE: The following string has to be consistent with the one defined in LectureUnit.java
    @Override
    public String getType() {
        return "attachment";
    }
}
