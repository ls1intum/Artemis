package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.User;

/**
 * An entity to store a students Judgement of Learning (JOL) value for a competency.
 * <p>
 * A JOL value is a students self-assessment of their own learning progress for a competency with value 1 to 5
 */
@Entity
@Table(name = "competency_jol")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class CompetencyJol extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "competency_id", nullable = false)
    private CourseCompetency competency;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "jol_value")
    private short value = 0;

    @Column(name = "judgement_time", nullable = false)
    private ZonedDateTime judgementTime;

    @Column(name = "competency_progress")
    private double competencyProgress;

    @Column(name = "competency_confidence")
    private double competencyConfidence;

    public CourseCompetency getCompetency() {
        return this.competency;
    }

    public void setCompetency(CourseCompetency competency) {
        this.competency = competency;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public short getValue() {
        return this.value;
    }

    public void setValue(short value) {
        this.value = value;
    }

    public ZonedDateTime getJudgementTime() {
        return this.judgementTime;
    }

    public void setJudgementTime(ZonedDateTime judgementTime) {
        this.judgementTime = judgementTime;
    }

    public double getCompetencyProgress() {
        return this.competencyProgress;
    }

    public void setCompetencyProgress(double progress) {
        this.competencyProgress = progress;
    }

    public double getCompetencyConfidence() {
        return this.competencyConfidence;
    }

    public void setCompetencyConfidence(double confidence) {
        this.competencyConfidence = confidence;
    }
}
