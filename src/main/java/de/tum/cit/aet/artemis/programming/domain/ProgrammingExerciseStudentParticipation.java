package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@Entity
@DiscriminatorValue(value = "PESP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {

    @Column(name = "repository_url")
    private String repositoryUri;

    @Column(name = "build_plan_id")
    private String buildPlanId;

    @Column(name = "branch")
    private String branch;

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

    public void setRepositoryUri(@NonNull LocalVCRepositoryUri repositoryUri) {
        this.repositoryUri = repositoryUri.getURI().toString();
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
