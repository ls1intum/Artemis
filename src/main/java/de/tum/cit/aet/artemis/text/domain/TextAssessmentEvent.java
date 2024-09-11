package de.tum.cit.aet.artemis.text.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.enumeration.FeedbackType;
import de.tum.cit.aet.artemis.domain.enumeration.TextAssessmentEventType;

/**
 * A TextAssessmentEvent.
 * This entity is used to save snapshots of the user interactions with the assessment system in Text exercises.
 */
@Entity
@Table(name = "text_assessment_event")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextAssessmentEvent extends DomainObject {

    @Column(name = "user_id")
    private Long userId;

    @CreatedDate
    @Column(name = "timestamp", updatable = false)
    private Instant timestamp = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private TextAssessmentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type")
    private FeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type")
    private TextBlockType segmentType;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "text_exercise_id")
    private Long textExerciseId;

    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "submission_id")
    private Long submissionId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public TextAssessmentEventType getEventType() {
        return eventType;
    }

    public void setEventType(TextAssessmentEventType event) {
        this.eventType = event;
    }

    public FeedbackType getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(FeedbackType feedbackType) {
        this.feedbackType = feedbackType;
    }

    public TextBlockType getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(TextBlockType segmentType) {
        this.segmentType = segmentType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getTextExerciseId() {
        return textExerciseId;
    }

    public void setTextExerciseId(Long textExerciseId) {
        this.textExerciseId = textExerciseId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(Long participationId) {
        this.participationId = participationId;
    }

    @Override
    public String toString() {
        return "TutorGroup{" + "id=" + getId() + ", userId='" + getUserId() + "'" + ", timestamp=" + getTimestamp() + ", event='" + getEventType() + "'" + ", feedbackType='"
                + getFeedbackType() + "'" + ", segmentType='" + getSegmentType() + "'" + ", courseId='" + getCourseId() + "'" + ", textExerciseId='" + getTextExerciseId() + "'"
                + ", submissionId='" + getSubmissionId() + "'" + ", participationId='" + getParticipationId() + "'" + "}";
    }
}
