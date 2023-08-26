package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

public record ExerciseDTO(long id, ExerciseType type) {

    public static ExerciseDTO of(Exercise exercise) {
        return new ExerciseDTO(exercise.getId(), exercise.getExerciseType());
    }
}
