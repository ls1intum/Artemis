package de.tum.cit.aet.artemis.lecture.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.JSON)
    private List<LectureTranscriptionSegment> segments;

    @OneToOne
    @JoinColumn(name = "lecture_unit_id", unique = true)
    private LectureUnit lectureUnit;

    /**
     * The external transcription job ID from the transcription service (e.g., Nebula).
     * Used to track and poll the status of the asynchronous transcription process.
     */
    @Column(name = "job_id", unique = true)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcription_status", nullable = false)
    private TranscriptionStatus transcriptionStatus = TranscriptionStatus.PENDING;

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public TranscriptionStatus getTranscriptionStatus() {
        return transcriptionStatus;
    }

    public void setTranscriptionStatus(TranscriptionStatus transcriptionStatus) {
        this.transcriptionStatus = transcriptionStatus;
    }

    @Override
    public String toString() {
        return "Transcription [language=" + language + ", segments=" + segments + ", jobId=" + jobId + ", status=" + transcriptionStatus + "]";
    }
}
