package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
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
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JoinColumn(name = "iris_assessment_id", referencedColumnName = "id", unique = true)
    @JsonIgnore
    private IrisAssessment irisAssessment;

    @Nullable
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JoinColumn(name = "iris_assessment_in_class_id", referencedColumnName = "id", unique = true)
    @JsonIgnore
    private IrisAssessment irisAssessmentInClass;

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

    @Nullable
    public IrisAssessment getIrisAssessment() {
        return irisAssessment;
    }

    public void setIrisAssessment(@Nullable IrisAssessment assessment) {
        this.irisAssessment = assessment;
    }

    @Nullable
    public IrisAssessment getIrisAssessmentInClass() {
        return irisAssessmentInClass;
    }

    public void setIrisAssessmentInClass(@Nullable IrisAssessment irisAssessmentInClass) {
        this.irisAssessmentInClass = irisAssessmentInClass;
    }
}
