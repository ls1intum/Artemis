package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.time.ZonedDateTime;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.enumeration.FeedbackConflictType;

/**
 * Represents the conflicts between two feedback of a text exercise.
 */
@Entity
@Table(name = "feedback_conflict")
public class FeedbackConflict implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conflict", nullable = false)
    private Boolean conflict;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "solved_at")
    private ZonedDateTime solvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private FeedbackConflictType type;

    @ManyToOne(optional = false)
    @JoinColumn(name = "first_feedback_id", referencedColumnName = "id")
    private Feedback firstFeedback;

    @ManyToOne(optional = false)
    @JoinColumn(name = "second_feedback_id", referencedColumnName = "id")
    private Feedback secondFeedback;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getConflict() {
        return conflict;
    }

    public void setConflict(Boolean conflict) {
        this.conflict = conflict;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getSolvedAt() {
        return solvedAt;
    }

    public void setSolvedAt(ZonedDateTime solvedAt) {
        this.solvedAt = solvedAt;
    }

    public FeedbackConflictType getType() {
        return type;
    }

    public void setType(FeedbackConflictType type) {
        this.type = type;
    }

    public Feedback getFirstFeedback() {
        return firstFeedback;
    }

    public void setFirstFeedback(Feedback firstFeedback) {
        this.firstFeedback = firstFeedback;
    }

    public Feedback getSecondFeedback() {
        return secondFeedback;
    }

    public void setSecondFeedback(Feedback secondFeedback) {
        this.secondFeedback = secondFeedback;
    }
}
