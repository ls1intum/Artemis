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

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "transcription")
public class Transcription extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnore
    private Lecture lecture;

    private String language;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("startTime asc")
    @JoinColumn(name = "transcription_id")
    private List<TranscriptionSegment> segments = new ArrayList<>();

    public Transcription() {
    }

    public Transcription(Lecture lecture, String language, List<TranscriptionSegment> segments) {
        this.lecture = lecture;
        this.language = language;
        this.segments = segments;
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

    public List<TranscriptionSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<TranscriptionSegment> segments) {
        this.segments = segments;
    }

    @Override
    public String toString() {
        return "Transcription [language=" + language + ", segments=" + segments + "]";
    }
}
