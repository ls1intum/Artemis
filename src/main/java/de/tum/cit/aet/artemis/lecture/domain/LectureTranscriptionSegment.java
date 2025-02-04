package de.tum.cit.aet.artemis.lecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "lecture_transcription_segments")
public class LectureTranscriptionSegment extends DomainObject {

    @NotNull
    @Column(name = "start_time")
    private Double startTime;

    @NotNull
    @Column(name = "end_time")
    private Double endTime;

    @Lob
    private String text;

    @Column(name = "slide_number")
    private int slideNumber;

    public LectureTranscriptionSegment() {
    }

    public LectureTranscriptionSegment(Double startTime, Double endTime, String text, int slideNumber) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.slideNumber = slideNumber;
    }

    public Double getStartTime() {
        return startTime;
    }

    public void setStartTime(Double startTime) {
        this.startTime = startTime;
    }

    public Double getEndTime() {
        return endTime;
    }

    public void setEndTime(Double endTime) {
        this.endTime = endTime;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    @Override
    public String toString() {
        return "LectureTranscriptionSegment [startTime = " + startTime + ", endTime = " + endTime + ", text = " + text + "]";
    }

    @AssertTrue(message = "End time must be greater than start time")
    private boolean isTimeValid() {
        return startTime == null || endTime == null || endTime > startTime;
    }

}
