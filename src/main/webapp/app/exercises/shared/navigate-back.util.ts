import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';

/**
 * Navigate from Assessment Editor to Dashboard:
 *   1. For Team Exercises: Navigate to Team Dashboard with all Submissions of the Team
 *   2. For Regular Exercises: Navigate to the Tutor Exercise Dashboard
 *   Fallback: If we do not know the exercise, we navigate back in the browsers history.
 *
 * @param location: Angular wrapper for interacting with Browser URL and History
 * @param router: Angular router to navigate to URL
 * @param exercise: Exercise currently assessed
 * @param submission: Submission currently assessed
 * @param isTestRun: flag to determine if it is an exam test run
 */
export function assessmentNavigateBack(location: Location, router: Router, exercise: Exercise | null, submission: Submission | null, isTestRun = false) {
    if (exercise) {
        const course = exercise.course || exercise.exerciseGroup?.exam?.course;

        if (isTestRun) {
            router.navigateByUrl(`/course-management/${course?.id}/exercises/${exercise.id}/test-run-tutor-dashboard`);
        } else {
            if (exercise.teamMode && submission) {
                const teamId = (submission.participation as StudentParticipation).team.id;
                router.navigateByUrl(`/courses/${course?.id}/exercises/${exercise.id}/teams/${teamId}`);
            } else {
                router.navigateByUrl(`/course-management/${course?.id}/exercises/${exercise.id}/tutor-dashboard`);
            }
        }
    } else {
        location.back();
    }
}
