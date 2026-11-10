import { NavigationExtras, Router } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

/** The navigation state key under which the editors of the non-programming exercise types receive an exercise read from a file. */
export const EXERCISE_FOR_IMPORT_FROM_FILE = 'exerciseForImportFromFile';

/**
 * The route segments, below a course or an exercise group route, and the navigation state that open the editor of an exercise read
 * from a file. Programming exercises have their own import route, which also uploads the repositories; the other types open the
 * editor of a new exercise filled with the settings from the file.
 *
 * @param exercise the exercise read from the file
 */
export function importFromFileNavigation(exercise: Exercise): { segments: string[]; extras: NavigationExtras } {
    if (exercise.type === ExerciseType.PROGRAMMING) {
        return { segments: ['programming-exercises', 'import-from-file'], extras: { state: { programmingExerciseForImportFromFile: exercise } } };
    }
    return { segments: [`${exercise.type}-exercises`, 'new'], extras: { state: { [EXERCISE_FOR_IMPORT_FROM_FILE]: exercise } } };
}

/**
 * Returns the exercise read from a file that the running navigation carries, placed into the given course or exercise group, or
 * undefined when the navigation opens the editor of an empty new exercise. Resolvers call it while the navigation runs.
 *
 * @param router the router of the running navigation
 * @param course the course of a course exercise
 * @param exerciseGroup the exercise group of an exam exercise
 */
export function exerciseForImportFromFile<T extends Exercise>(router: Router, course: Course | undefined, exerciseGroup: ExerciseGroup | undefined): T | undefined {
    const exercise = router.currentNavigation()?.extras.state?.[EXERCISE_FOR_IMPORT_FROM_FILE] as T | undefined;
    if (exercise) {
        // The file still names the course or exam of the source; the server would attach the copy to whichever is set.
        exercise.course = course;
        exercise.exerciseGroup = exerciseGroup;
    }
    return exercise;
}
