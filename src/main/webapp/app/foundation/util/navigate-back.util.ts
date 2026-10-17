import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * Navigate from Assessment Editor to the Exercise Assessment Dashboard, which also lists the teams of a team tutor.
 *   Fallback: If we do not know the exercise, we navigate back in the browser's history.
 *
 * @param location: Angular wrapper for interacting with Browser URL and History
 * @param router: Angular router to navigate to URL
 * @param exercise: Exercise currently assessed
 * @param isTestRun: flag to determine if it is an exam test run
 */
export function assessmentNavigateBack(location: Location, router: Router, exercise?: Exercise, isTestRun = false) {
    if (exercise) {
        const course = getCourseFromExercise(exercise);

        if (isTestRun) {
            const exam = exercise.exerciseGroup!.exam!;
            void router.navigateByUrl(`/course-management/${course?.id}/exams/${exam.id}/test-assessment-dashboard/${exercise.id}`);
        } else {
            if (exercise.exerciseGroup) {
                const exam = exercise.exerciseGroup.exam!;
                void router.navigateByUrl(`/course-management/${course?.id}/exams/${exam.id}/assessment-dashboard/${exercise.id}`);
            } else {
                void router.navigateByUrl(`/course-management/${course?.id}/assessment-dashboard/${exercise.id}`);
            }
        }
    } else {
        location.back();
    }
}
