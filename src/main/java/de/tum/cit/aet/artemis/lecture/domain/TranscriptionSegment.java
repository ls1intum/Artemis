package de.tum.cit.aet.artemis.lecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "transcription_segments")
public class TranscriptionSegment extends DomainObject {

    @Column(name = "start_time")
    private Double startTime;

    @Column(name = "end_time")
    private Double endTime;

    private String text;

    @ManyToOne
    @JoinColumn(name = "lecture_unit_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private LectureUnit lectureUnit;

    public TranscriptionSegment() {
    }

    public TranscriptionSegment(Double startTime, Double endTime, String text, LectureUnit lectureUnit) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.lectureUnit = lectureUnit;
    }
}
