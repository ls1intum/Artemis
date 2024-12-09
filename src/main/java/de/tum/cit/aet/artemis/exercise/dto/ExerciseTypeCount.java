package de.tum.cit.aet.artemis.exercise.dto;

import java.util.Objects;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

public class ExerciseTypeCount {

    private final ExerciseType exerciseType;

    private final long count;

    public ExerciseTypeCount(Class<?> exerciseType, Long count) {
        this.exerciseType = ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class));
        this.count = Objects.requireNonNullElse(count, 0L);
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public long getCount() {
        return count;
    }
}
