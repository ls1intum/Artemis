import { Exercise, ExerciseType } from 'app/entities/exercise.model';

type ExerciseId = number;

/**
 * Maps exercise types to exercises of this type. For each of such exercises a value can be stored.
 */
export class ExerciseTypeStatisticsMap extends Map<ExerciseType, Map<ExerciseId, number>> {
    /**
     * Returns the value stored under the exercise type and exercise in this map.
     * @param exerciseType The type the exercise belongs to.
     * @param exercise The exercise for which a value should be returned.
     */
    public getValue(exerciseType: ExerciseType, exercise: Exercise): number | undefined {
        return this.get(exerciseType)?.get(exercise.id!);
    }

    /**
     * Stores a value for the given exercise and type in this map.
     * @param exerciseType The type the exercise belongs to.
     * @param exercise The exercise for which a value should be stored.
     * @param value The value that should be stores in this map.
     */
    public setValue(exerciseType: ExerciseType, exercise: Exercise, value: number) {
        if (!this.get(exerciseType)) {
            this.set(exerciseType, new Map());
        }

        this.get(exerciseType)!.set(exercise.id!, value);
    }
}
