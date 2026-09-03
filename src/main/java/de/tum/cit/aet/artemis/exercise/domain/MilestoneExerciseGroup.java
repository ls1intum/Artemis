package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;

/**
 * A {@code MilestoneExerciseGroup} bundles the {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}s of one
 * Scrum-style milestone/sprint around a single, shared set of repositories.
 * <p>
 * Unlike a plain {@link ExerciseVariantGroup}, whose members are interchangeable variants, a milestone group anchors one
 * {@link MilestoneExercise} — created and deleted together with the group, never a member of {@link #getExercises()} — that
 * owns the actual repositories, build plan, and shared test case pool the group's exercises work against.
 * <p>
 * The group's timeline getters/setters are overridden to delegate to the {@link #milestoneExercise}: editing the group's
 * timeline (via the existing {@code ExerciseVariantGroupService} push mechanism, unchanged) transparently edits the
 * {@link MilestoneExercise}'s own dates, which is exactly what "a UserStory's timeline references the Milestone Exercise's
 * values" means once those dates are, in turn, pushed onto every {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise}
 * member the same way a plain group's timeline is pushed onto its variants.
 */
@Entity
@DiscriminatorValue("M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MilestoneExerciseGroup extends ExerciseVariantGroup {

    // No CascadeType.PERSIST: the milestone exercise is always created beforehand through the dedicated programming-exercise
    // creation pipeline (see ExerciseVariantGroupResource.createMilestoneExerciseGroup) and is already persisted - possibly in
    // a different persistence context - by the time it's assigned here. Cascading PERSIST onto that already-row-backed but
    // detached instance makes Hibernate throw "Detached entity passed to persist" when the group itself is then saved.
    // No CascadeType.REMOVE / orphanRemoval: deleting the milestone exercise is always done explicitly through
    // ProgrammingExerciseDeletionService (see ExerciseVariantGroupResource.deleteExerciseVariantGroup), which does the
    // ordered, VCS/CI-aware cleanup (build plans, repositories, participations, access tokens). A raw JPA cascade -
    // whether from CascadeType.REMOVE or from orphanRemoval firing when the reference is nulled - deletes just the
    // exercise row and hits FK violations on anything that cleanup would have handled (e.g. participation_vcs_access_token).
    @OneToOne(fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH })
    @JoinColumn(name = "milestone_exercise_id")
    @JsonIgnoreProperties({ "exerciseVariantGroup", "course" })
    private MilestoneExercise milestoneExercise;

    public MilestoneExercise getMilestoneExercise() {
        return milestoneExercise;
    }

    public void setMilestoneExercise(MilestoneExercise milestoneExercise) {
        this.milestoneExercise = milestoneExercise;
    }

    /**
     * Whether {@link #milestoneExercise} can be safely dereferenced: not just non-null, but also not an uninitialized
     * Hibernate proxy. The timeline getters below delegate to it, and countless call sites across the codebase load an
     * {@code Exercise} (whose {@code exerciseVariantGroup} may be a {@code MilestoneExerciseGroup}) without fetch-joining
     * this further {@code LAZY} association - with {@code spring.jpa.open-in-view} disabled, dereferencing it after the
     * loading session has closed throws {@code LazyInitializationException}. Treating "not fetched" the same as "not set"
     * (returning {@code null}/no-op instead of crashing) trades perfectly accurate dates for the callers that didn't
     * fetch it for basic robustness everywhere else; callers that need it fetch it explicitly (see
     * {@code ExerciseVariantGroupRepository.findMilestoneExerciseByGroupId} and the queries with
     * {@code TREAT(... AS MilestoneExerciseGroup).milestoneExercise}).
     */
    private boolean isMilestoneExerciseAvailable() {
        return milestoneExercise != null && Hibernate.isInitialized(milestoneExercise);
    }

    /**
     * A milestone group's points are never an independently set cap - they're always the sum of its
     * {@link de.tum.cit.aet.artemis.programming.domain.UserStoryExercise} members' points (see
     * {@link de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupDTO}, which computes that sum for display). Keeping
     * the stored column {@code null} here also gives the right grading behaviour for free: {@code VariantGroupCappedSum}
     * only caps a group's achieved points when {@code maxPoints} is non-null, so an always-null column means the group's
     * full, uncapped point sum counts - exactly "sum of all included user story exercises".
     */
    @Override
    public void setMaxPoints(@Nullable Double maxPoints) {
        // Intentionally ignored - see the javadoc above.
    }

    @Override
    @Nullable
    public ZonedDateTime getReleaseDate() {
        return isMilestoneExerciseAvailable() ? milestoneExercise.getReleaseDate() : null;
    }

    @Override
    public void setReleaseDate(@Nullable ZonedDateTime releaseDate) {
        if (isMilestoneExerciseAvailable()) {
            milestoneExercise.setReleaseDate(releaseDate);
        }
    }

    @Override
    @Nullable
    public ZonedDateTime getStartDate() {
        return isMilestoneExerciseAvailable() ? milestoneExercise.getStartDate() : null;
    }

    @Override
    public void setStartDate(@Nullable ZonedDateTime startDate) {
        if (isMilestoneExerciseAvailable()) {
            milestoneExercise.setStartDate(startDate);
        }
    }

    @Override
    @Nullable
    public ZonedDateTime getDueDate() {
        return isMilestoneExerciseAvailable() ? milestoneExercise.getDueDate() : null;
    }

    @Override
    public void setDueDate(@Nullable ZonedDateTime dueDate) {
        if (isMilestoneExerciseAvailable()) {
            milestoneExercise.setDueDate(dueDate);
        }
    }

    @Override
    @Nullable
    public ZonedDateTime getAssessmentDueDate() {
        return isMilestoneExerciseAvailable() ? milestoneExercise.getAssessmentDueDate() : null;
    }

    @Override
    public void setAssessmentDueDate(@Nullable ZonedDateTime assessmentDueDate) {
        if (isMilestoneExerciseAvailable()) {
            milestoneExercise.setAssessmentDueDate(assessmentDueDate);
        }
    }

    @Override
    @Nullable
    public ZonedDateTime getExampleSolutionPublicationDate() {
        return isMilestoneExerciseAvailable() ? milestoneExercise.getExampleSolutionPublicationDate() : null;
    }

    @Override
    public void setExampleSolutionPublicationDate(@Nullable ZonedDateTime exampleSolutionPublicationDate) {
        if (isMilestoneExerciseAvailable()) {
            milestoneExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        }
    }
}
