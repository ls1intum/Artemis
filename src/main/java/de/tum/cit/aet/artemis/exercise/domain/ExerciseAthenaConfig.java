package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Stores Athena specific configuration for an exercise
 */
@Entity
@Table(name = "exercise_athena_config")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseAthenaConfig extends DomainObject {

    @OneToOne(mappedBy = "athenaConfig", fetch = FetchType.LAZY)
    @JsonIgnoreProperties("athenaConfig")
    private Exercise exercise;

    @Column(name = "preliminary_feedback_module")
    private String preliminaryFeedbackModule;

    @Column(name = "feedback_suggestion_module")
    private String feedbackSuggestionModule;

    public ExerciseAthenaConfig() {
    }

    public ExerciseAthenaConfig(ExerciseAthenaConfig other) {
        this.preliminaryFeedbackModule = other.getPreliminaryFeedbackModule();
        this.feedbackSuggestionModule = other.getFeedbackSuggestionModule();
    }

    public static ExerciseAthenaConfig of(String feedbackSuggestionModule, String preliminaryFeedbackModule) {
        ExerciseAthenaConfig config = new ExerciseAthenaConfig();
        config.setFeedbackSuggestionModule(feedbackSuggestionModule);
        config.setPreliminaryFeedbackModule(preliminaryFeedbackModule);
        return config;
    }

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

    public String getFeedbackSuggestionModule() {
        return feedbackSuggestionModule;
    }

    public void setFeedbackSuggestionModule(String feedbackSuggestionModule) {
        this.feedbackSuggestionModule = feedbackSuggestionModule;
    }

    public boolean isEmpty() {
        return feedbackSuggestionModule == null && preliminaryFeedbackModule == null;
    }
}
