package de.tum.in.www1.artemis.domain.participation;

import java.net.MalformedURLException;
import java.net.URL;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.view.QuizView;

@MappedSuperclass
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class AbstractBaseProgrammingExerciseParticipation extends Participation implements ProgrammingExerciseParticipation {

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUrl;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    /**
     * @return the repository URL as an URL Object
     */
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
    @JsonIgnore
    public Exercise getExercise() {
        return getProgrammingExercise();
    }

    @Override
    public void setExercise(Exercise exercise) {
        if (exercise == null) {
            setProgrammingExercise(null);
        }
        else if (exercise instanceof ProgrammingExercise) {
            setProgrammingExercise((ProgrammingExercise) exercise);
        }
    }

    @Override
    public String toString() {
        return "Participation{" + "id=" + getId() + ", repositoryUrl='" + getRepositoryUrl() + "'" + ", buildPlanId='" + getBuildPlanId() + "}";
    }
}
