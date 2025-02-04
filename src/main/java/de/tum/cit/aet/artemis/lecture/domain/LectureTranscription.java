package de.tum.cit.aet.artemis.lecture.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "lecture_transcription")
public class LectureTranscription extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnore
    private Lecture lecture;

    @Size(min = 2, max = 2, message = "Language must be exactly 2 characters long")
    private String language;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("startTime asc")
    @JoinColumn(name = "transcription_id")
    private List<LectureTranscriptionSegment> segments = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "lecture_unit_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnore
    private LectureUnit lectureUnit;

    public LectureTranscription() {
    }

    public LectureTranscription(Lecture lecture, String language, List<LectureTranscriptionSegment> segments, LectureUnit lectureUnit) {
        this.lecture = lecture;
        this.language = language;
        this.segments = segments;
        this.lectureUnit = lectureUnit;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<LectureTranscriptionSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<LectureTranscriptionSegment> segments) {
        this.segments = segments;
    }

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    @Override
    public String toString() {
        return "Transcription [language=" + language + ", segments=" + segments + "]";
    }
}
