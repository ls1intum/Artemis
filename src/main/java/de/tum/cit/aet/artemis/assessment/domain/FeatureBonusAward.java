package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * Points a tutor awards a student or team for a feature they implemented beyond the task of an exercise. The points do not
 * change the score of the exercise: the course converts the sum of a student's awards into a bonus on the final grade.
 * <p>
 * An award belongs to the participation, not to a result, because a programming exercise gets a new result on every push.
 */
@Entity
@Table(name = "feature_bonus_award")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FeatureBonusAward extends AbstractAuditingEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "participation_id", nullable = false, updatable = false)
    @JsonIgnore
    @Parent
    private StudentParticipation participation;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "justification", length = 2000)
    private String justification;

    @Column(name = "points", nullable = false)
    private double points;

    public StudentParticipation getParticipation() {
        return participation;
    }

    public void setParticipation(StudentParticipation participation) {
        this.participation = participation;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = points;
    }
}
