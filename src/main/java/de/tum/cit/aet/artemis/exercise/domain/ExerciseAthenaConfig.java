package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Stores Athena module configuration for an exercise, such as feedback module names.
 */
@Entity
@Table(name = "exercise_athena_config")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseAthenaConfig extends DomainObject {

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "exercise_id", nullable = false, unique = true)
    private Exercise exercise;

    @Column(name = "preliminary_feedback_module", length = 255)
    private String preliminaryFeedbackModule;

    @Column(name = "graded_feedback_module", length = 255)
    private String gradedFeedbackModule;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public String getPreliminaryFeedbackModule() {
        return preliminaryFeedbackModule;
    }

    public void setPreliminaryFeedbackModule(String preliminaryFeedbackModule) {
        this.preliminaryFeedbackModule = preliminaryFeedbackModule;
    }

    public String getGradedFeedbackModule() {
        return gradedFeedbackModule;
    }

    public void setGradedFeedbackModule(String gradedFeedbackModule) {
        this.gradedFeedbackModule = gradedFeedbackModule;
    }
}
