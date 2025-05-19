package de.tum.cit.aet.artemis.lecture.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "lecture_transcription")
public class LectureTranscription extends DomainObject {

    /**
     * The language spoken in the video that is transcribed, given as a language key e.g. "en" or "de"
     */
    @Size(min = 2, max = 2, message = "Language must be exactly 2 characters long")
    @Column(name = "language")
    private String language;

    @Convert(converter = LectureTranscriptionSegmentConverter.class)
    @Column(name = "segments", columnDefinition = "json")
    private List<LectureTranscriptionSegment> segments;

    @OneToOne
    @JoinColumn(name = "lecture_unit_id", unique = true)
    private LectureUnit lectureUnit;

    public LectureTranscription() {
    }

    public LectureTranscription(String language, List<LectureTranscriptionSegment> segments, LectureUnit lectureUnit) {
        this.language = language;
        this.segments = segments;
        this.lectureUnit = lectureUnit;
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
