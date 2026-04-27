package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseAthenaConfigDTO(Long id, Long exerciseId, String preliminaryFeedbackModule, String gradedFeedbackModule) {

    /**
     * Creates an ExerciseAthenaConfigDTO from the given ExerciseAthenaConfig domain object.
     *
     * @param config the config to convert, may be null
     * @return the corresponding DTO, or null if config is null
     */
    public static ExerciseAthenaConfigDTO from(ExerciseAthenaConfig config) {
        if (config == null) {
            return null;
        }
        Long exId = config.getExercise() != null ? config.getExercise().getId() : null;
        return new ExerciseAthenaConfigDTO(config.getId(), exId, config.getPreliminaryFeedbackModule(), config.getGradedFeedbackModule());
    }
}
