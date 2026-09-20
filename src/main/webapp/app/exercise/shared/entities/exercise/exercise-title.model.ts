import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * The minimum needed to list an exercise for selection, returned by
 * `GET api/exercise/courses/{courseId}/exercise-titles`.
 *
 * For callers that only have to name exercises rather than show them.
 */
export interface ExerciseTitle {
    id: number;
    title?: string;
    type?: ExerciseType;
}
