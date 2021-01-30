package de.tum.in.www1.artemis.domain.scores;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;

@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "participant_score")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("PS")
@DiscriminatorOptions(force = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Annotation necessary to distinguish between concrete implementations of ParticipantScore when deserializing from JSON
@JsonSubTypes({ @JsonSubTypes.Type(value = StudentScore.class, name = "studentScore"), @JsonSubTypes.Type(value = TeamScore.class, name = "teamScore") })
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
    private Long lastScore;

    @Column(name = "last_rated_score")
    private Long lastRatedScore;

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

    public Long getLastScore() {
        return lastScore;
    }

    public void setLastScore(Long lastScore) {
        this.lastScore = lastScore;
    }

    public Long getLastRatedScore() {
        return lastRatedScore;
    }

    public void setLastRatedScore(Long lastRatedScore) {
        this.lastRatedScore = lastRatedScore;
    }
}
