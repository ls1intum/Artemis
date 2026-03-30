package de.tum.cit.aet.artemis.programming.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;

@Entity
@DiscriminatorValue(value = "PESP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {

    @Size(max = 255)
    @Column(name = "repository_url")
    private String repositoryUri;

    @Column(name = "build_plan_id")
    private String buildPlanId;

    @Column(name = "branch")
    private String branch;

    @Nullable
    @Column(name = "iris_verdict")
    private String irisVerdict;

    @Nullable
    @Column(name = "iris_verified_score")
    private Double irisVerifiedScore;

    @ElementCollection
    @CollectionTable(name = "participation_iris_reasoning", joinColumns = @JoinColumn(name = "participation_id"))
    @Column(name = "reason")
    private List<String> irisReasoning = new ArrayList<>();

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

    @Nullable
    public List<String> getIrisReasoning() {
        return irisReasoning;
    }

    public void setIrisReasoning(@Nullable List<String> irisReasoning) {
        this.irisReasoning = irisReasoning;
    }

    @Nullable
    public String getIrisVerdict() {
        return irisVerdict;
    }

    public void setIrisVerdict(@Nullable String irisVerdict) {
        this.irisVerdict = irisVerdict;
    }

    @Nullable
    public Double getIrisVerifiedScore() {
        return irisVerifiedScore;
    }

    public void setIrisVerifiedScore(@Nullable Double irisVerifiedScore) {
        this.irisVerifiedScore = irisVerifiedScore;
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
