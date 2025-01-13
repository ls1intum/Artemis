package de.tum.cit.aet.artemis.lecture.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "transcription")
public class Transcription extends DomainObject {

    private String language;

    @ManyToOne
    @JoinColumn(name = "lecture_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Lecture lecture;

    @OneToMany(cascade = CascadeType.ALL)
    @OrderColumn(name = "start_time")
    @JoinColumn(name = "transcrition_id")
    private List<TranscriptionSegment> segments = new ArrayList<>();

    public Transcription() {
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    public List<TranscriptionSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<TranscriptionSegment> segments) {
        this.segments = segments;
    }
}
