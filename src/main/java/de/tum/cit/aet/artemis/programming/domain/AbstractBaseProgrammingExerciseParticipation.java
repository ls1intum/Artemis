package de.tum.cit.aet.artemis.programming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

@MappedSuperclass
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class AbstractBaseProgrammingExerciseParticipation extends Participation implements ProgrammingExerciseParticipation {

    @Column(name = "repository_url")
    @JsonView(QuizView.Before.class)
    private String repositoryUri;

    @Column(name = "build_plan_id")
    @JsonView(QuizView.Before.class)
    private String buildPlanId;

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
    public void filterSensitiveInformation() {
        // nothing to filter here
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "id=" + getId() + ", repositoryUri='" + getRepositoryUri() + "'" + ", buildPlanId='" + getBuildPlanId() + "}";
    }
}
