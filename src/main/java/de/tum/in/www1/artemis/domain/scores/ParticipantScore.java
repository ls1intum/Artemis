package de.tum.in.www1.artemis.domain.scores;

import java.time.Instant;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;

/**
 * Participant scores store the last (rated) result for each student/team and exercise combination.
 * They are eventually consistent within a few seconds.
 * <p><b>Background:</b>
 * Normally, getting the last result means going through the chain Exercise -> Participation -> Submission -> Result.
 * This is inefficient for certain scenarios, e.g., when calculating the course average score for an exercise.</p>
 * @see de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "participant_score")
@EntityListeners(AuditingEntityListener.class)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PS")
@DiscriminatorOptions(force = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of ParticipantScore when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentScore.class, name = "studentScore"), @JsonSubTypes.Type(value = TeamScore.class, name = "teamScore") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class ParticipantScore extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    /**
     * Last result of the participant for the exercise no matter if the result is rated or not
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_result_id")
    private Result lastResult;

    /**
     * Last rated result of the participant for the exercise
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_rated_result_id")
    private Result lastRatedResult;

    @Column(name = "last_score")
    private Double lastScore;

    @Column(name = "last_points")
    private Double lastPoints;

    @Column(name = "last_rated_score")
    private Double lastRatedScore;

    @Column(name = "last_rated_points")
    private Double lastRatedPoints;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    @JsonIgnore
    private Instant lastModifiedDate;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Result getLastResult() {
        return lastResult;
    }

    public void setLastResult(Result lastResult) {
        this.lastResult = lastResult;
    }

    public Result getLastRatedResult() {
        return lastRatedResult;
    }

    public void setLastRatedResult(Result lastRatedResult) {
        this.lastRatedResult = lastRatedResult;
    }

    public Double getLastScore() {
        return lastScore;
    }

    public void setLastScore(Double lastScore) {
        this.lastScore = lastScore;
    }

    public Double getLastRatedScore() {
        return lastRatedScore;
    }

    public void setLastRatedScore(Double lastRatedScore) {
        this.lastRatedScore = lastRatedScore;
    }

    public Double getLastPoints() {
        return lastPoints;
    }

    public void setLastPoints(Double lastPoints) {
        this.lastPoints = lastPoints;
    }

    public Double getLastRatedPoints() {
        return lastRatedPoints;
    }

    public void setLastRatedPoints(Double lastRatedPoints) {
        this.lastRatedPoints = lastRatedPoints;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    @Override
    public String toString() {
        return "ParticipantScore{" + "exercise=" + exercise.getId() + ", lastResult=" + lastResult.getId() + ", lastScore=" + lastScore + ", lastPoints=" + lastPoints
                + ", lastRatedResult=" + lastRatedResult.getId() + ", lastRatedScore=" + lastRatedScore + ", lastRatedPoints=" + lastRatedPoints + ", lastModifiedDate="
                + lastModifiedDate + '}';
    }
}
