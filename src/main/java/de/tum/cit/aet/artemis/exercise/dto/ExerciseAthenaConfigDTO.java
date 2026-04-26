package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseAthenaConfigDTO {

    private Long id;

    private Long exerciseId;

    private String preliminaryFeedbackModule;

    private String gradedFeedbackModule;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
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
