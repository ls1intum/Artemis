package de.tum.in.www1.artemis.domain.participation;

import java.net.MalformedURLException;
import java.net.URL;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@DiscriminatorValue(value = "TPEP")
public class TemplateProgrammingExerciseParticipation extends Participation implements ProgrammingExerciseParticipation {

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUrl;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

    @OneToOne(mappedBy = "templateParticipation")
    @JsonIgnoreProperties("templateParticipation")
    private ProgrammingExercise programmingExercise;

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
    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    @Override
    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    @Override
    public String toString() {
        return "Participation{" + "id=" + getId() + ", repositoryUrl='" + getRepositoryUrl() + "'" + ", buildPlanId='" + getBuildPlanId() + "}";
    }

    @Override
    public Participation copyParticipationId() {
        var participation = new TemplateProgrammingExerciseParticipation();
        participation.setId(getId());
        return participation;
    }
}
