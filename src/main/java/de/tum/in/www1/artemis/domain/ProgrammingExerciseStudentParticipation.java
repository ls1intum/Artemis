package de.tum.in.www1.artemis.domain;

import java.net.MalformedURLException;
import java.net.URL;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "PESP")
public class ProgrammingExerciseStudentParticipation extends StudentParticipation implements ProgrammingExerciseParticipation {

    private static final long serialVersionUID = 1L;

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUrl;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public Participation repositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
        return this;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public Participation buildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
        return this;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    @JsonIgnore
    public URL getRepositoryUrlAsUrl() {
        if (repositoryUrl == null) {
            return null;
        }

        try {
            return new URL(repositoryUrl);
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
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
    public void addSubmissions(ProgrammingSubmission programmingSubmission) {
        this.getSubmissions().add(programmingSubmission);
    }

    @Override
    public String toString() {
        return "Participation{" + "id=" + getId() + ", repositoryUrl='" + getRepositoryUrl() + "'" + ", buildPlanId='" + getBuildPlanId() + "'" + ", initializationState='"
                + getInitializationState() + "'" + ", initializationDate='" + getInitializationDate() + "'" + ", presentationScore=" + getPresentationScore() + "}";
    }
}
