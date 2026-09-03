package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * The effort a student reports for one {@link UserStoryExercise}: what they estimated it would take before working on
 * it, and what it actually took.
 * <p>
 * Keyed by {@link Participation} rather than by submission or exercise. A {@link ProgrammingSubmission} is created
 * automatically from a git push, so there is no form in which a student could attach these numbers to one, and they
 * would have to be re-entered on every push. The participation also settles the team case for free: a team has exactly
 * one participation, so its members share one pair.
 * <p>
 * Stored in its own table rather than as columns on {@code participation}: that table is {@code SINGLE_TABLE} for every
 * participation of every exercise type, and this is a user-story-only concern. Mirrors how {@code Rating} hangs off a
 * {@code Result}.
 * <p>
 * Both values are nullable - a student may record the estimate and come back for the actual effort later. "Reported in
 * full" means both are set; only {@link #getEstimatedEffort()} gates pushes (see {@code MilestoneEffortGateService}),
 * since the actual effort is by definition unknowable before the work is done.
 */
@Entity
@Table(name = "user_story_effort")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserStoryEffort extends DomainObject {

    /** Hours the participant expected the story to take, as reported by them. */
    @Nullable
    @Column(name = "estimated_effort")
    private Double estimatedEffort;

    /** Hours the story actually took, as reported by the participant. */
    @Nullable
    @Column(name = "actual_effort")
    private Double actualEffort;

    @OneToOne
    @JoinColumn(name = "participation_id")
    private Participation participation;

    @Nullable
    public Double getEstimatedEffort() {
        return estimatedEffort;
    }

    public void setEstimatedEffort(@Nullable Double estimatedEffort) {
        this.estimatedEffort = estimatedEffort;
    }

    @Nullable
    public Double getActualEffort() {
        return actualEffort;
    }

    public void setActualEffort(@Nullable Double actualEffort) {
        this.actualEffort = actualEffort;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    @Override
    public String toString() {
        return "UserStoryEffort{id=" + getId() + ", estimatedEffort=" + estimatedEffort + ", actualEffort=" + actualEffort + "}";
    }
}
