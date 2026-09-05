import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

/**
 * The child route segments that show a participation for an exercise, relative to the exercises route.
 * <p>
 * The URL is what decides which participation the exercise details show — `syncModeWithRoutedParticipation` derives the
 * graded/practice mode from it — so anything that changes which participation is displayed has to go through here
 * rather than through component state alone. Shared by the split panel's redirect and by the details component, which
 * re-points the URL when a practice participation is started, so the two cannot disagree about where a participation
 * lives.
 *
 * @param exercise      the exercise being shown
 * @param participation the participation to route to
 * @return the segments to pass to `Router.navigate`, or undefined for an exercise type that has no participation route
 *         (a quiz routes by mode rather than by participation id)
 */
export function participationChildRouteSegments(exercise: Exercise, participation: StudentParticipation): (string | number)[] | undefined {
    if (!exercise.id || !participation.id) {
        return undefined;
    }
    switch (exercise.type) {
        case ExerciseType.TEXT:
            return ['text-exercises', exercise.id, 'participate', participation.id];
        case ExerciseType.PROGRAMMING:
            return (exercise as ProgrammingExercise).allowOnlineEditor ? ['programming-exercises', exercise.id, 'code-editor', participation.id] : undefined;
        case ExerciseType.MODELING:
            return ['modeling-exercises', exercise.id, 'participate', participation.id];
        case ExerciseType.FILE_UPLOAD:
            return ['file-upload-exercises', exercise.id, 'participate', participation.id];
        default:
            return undefined;
    }
}
