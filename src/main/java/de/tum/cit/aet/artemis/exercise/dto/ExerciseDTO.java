package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A DTO representing an exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseDTO {

    private long id;

    private ExerciseType type;

    private ExerciseAthenaConfigDTO athenaConfig;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ExerciseType getType() {
        return type;
    }

    public void setType(ExerciseType type) {
        this.type = type;
    }

    public ExerciseAthenaConfigDTO getAthenaConfig() {
        return athenaConfig;
    }

    public void setAthenaConfig(ExerciseAthenaConfigDTO athenaConfig) {
        this.athenaConfig = athenaConfig;
    }

    /**
     * Converts an exercise to an exercise DTO.
     *
     * @param exercise the exercise to convert
     * @return the exercise DTO
     */
    public static ExerciseDTO of(Exercise exercise) {
        ExerciseDTO dto = new ExerciseDTO();
        dto.setId(exercise.getId());
        dto.setType(exercise.getExerciseType());
        dto.setAthenaConfig(ExerciseAthenaConfigDTO.from(exercise.getAthenaConfig()));
        return dto;
    }
}
