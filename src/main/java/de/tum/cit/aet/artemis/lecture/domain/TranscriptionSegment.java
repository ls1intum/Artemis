package de.tum.cit.aet.artemis.lecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "transcription_segments")
public class TranscriptionSegment extends DomainObject {

    @Column(name = "start_time")
    private Double startTime;

    @Column(name = "end_time")
    private Double endTime;

    @Lob
    private String text;

    @ManyToOne
    @JoinColumn(name = "lecture_unit_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnore
    private LectureUnit lectureUnit;

    @Column(name = "slide_number")
    private int slideNumber;

    public TranscriptionSegment() {
    }

    public TranscriptionSegment(Double startTime, Double endTime, String text, LectureUnit lectureUnit, int slideNumber) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.lectureUnit = lectureUnit;
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

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    public int getSlideNumber() {
        return slideNumber;
    }

    public void setSlideNumber(int slideNumber) {
        this.slideNumber = slideNumber;
    }

    @Override
    public String toString() {
        return "TranscriptionSegment [startTime = " + startTime + ", endTime = " + endTime + ", text = " + text + "]";
    }
}
