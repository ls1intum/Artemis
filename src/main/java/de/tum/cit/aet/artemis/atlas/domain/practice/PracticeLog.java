package de.tum.cit.aet.artemis.atlas.domain.practice;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * A PracticeLog records the delivery of a single quiz question from the AI Quiz Question Pool within a PracticeSession.
 * It stores whether the item was allocated adaptively or as non-adaptive control (50/50 split),
 * the competency and difficulty metadata, the student's submitted response, score, and the submission timestamp.
 */
@Entity
@Table(name = "practice_log")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PracticeLog extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_session_id", nullable = false)
    @Parent
    private PracticeSession practiceSession;

    @Column(name = "pool_item_id", nullable = false)
    private Long poolItemId;

    @Column(name = "competency_id")
    private Long competencyId;

    @Column(name = "difficulty")
    private String difficulty;

    @Column(name = "is_adaptive", nullable = false)
    private boolean isAdaptive;

    @Column(name = "score")
    private Double score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_answer")
    private String submittedAnswer;

    @Column(name = "answered_at")
    private ZonedDateTime answeredAt;

    public void setPracticeSession(PracticeSession practiceSession) {
        this.practiceSession = practiceSession;
    }

    public Long getPoolItemId() {
        return poolItemId;
    }

    public void setPoolItemId(Long poolItemId) {
        this.poolItemId = poolItemId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public void setAdaptive(boolean isAdaptive) {
        this.isAdaptive = isAdaptive;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(String submittedAnswer) {
        this.submittedAnswer = submittedAnswer;
    }

    public void setAnsweredAt(ZonedDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
