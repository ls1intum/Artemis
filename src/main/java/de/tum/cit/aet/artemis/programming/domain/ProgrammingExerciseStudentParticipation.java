package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.config.QuizView;
import de.tum.cit.aet.artemis.service.connectors.vcs.AbstractVersionControlService;

@Entity
@DiscriminatorValue(value = "PESP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUri;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

    @Column(name = "branch")
    @JsonView(QuizView.Before.class)
    private String branch;

    /**
     * Defines if the participation is locked, i.e. if the student can currently not make any submissions.
     * This takes into account: the start date of the exercise (or the exam), the (individual) due date, and the lock repository policy.
     * Course exercise practice repositories and instructor exam test run repositories will never be locked.
     * <p>
     * Important: this boolean flag must only be used for course programming exercises and is irrelevant for exam programming exercises!!!
     *
     */
    @Column(name = "locked")
    @JsonView(QuizView.Before.class)
    private boolean locked;

    public ProgrammingExerciseStudentParticipation() {
        // Default constructor
    }

    public ProgrammingExerciseStudentParticipation(String branch) {
        this.branch = branch;
    }

    @Override
    public String getRepositoryUri() {
        return repositoryUri;
    }

    @Override
    public void setRepositoryUri(String repositoryUri) {
        this.repositoryUri = repositoryUri;
    }

    @Override
    public String getBuildPlanId() {
        return buildPlanId;
    }

    @Override
    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    /**
     * Getter for the stored default branch of the participation.
     * Use {@link AbstractVersionControlService#getOrRetrieveBranchOfStudentParticipation(ProgrammingExerciseStudentParticipation)} if you are not sure that the value was already
     * set in the Artemis database
     *
     * @return the name of the default branch or null if not yet stored in Artemis
     */
    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    @JsonIgnore
    // NOTE: this is a helper method to avoid casts in other classes that want to access the underlying exercise
    public ProgrammingExercise getProgrammingExercise() {
        Exercise exercise = getExercise();
        if (exercise instanceof ProgrammingExercise) { // this should always be the case except exercise is null
            return (ProgrammingExercise) exercise;
        }
        else {
            return null;
        }
    }

    @Override
    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        setExercise(programmingExercise);
    }

    @Override
    public String getType() {
        return "programming";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", repositoryUri='" + getRepositoryUri() + "'" + ", buildPlanId='" + getBuildPlanId() + "'"
                + ", initializationState='" + getInitializationState() + "'" + ", initializationDate='" + getInitializationDate() + "'" + ", individualDueDate="
                + getIndividualDueDate() + "'" + ", presentationScore=" + getPresentationScore() + "}";
    }

}
