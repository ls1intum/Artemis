import { Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Location } from '@angular/common';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { filter, skip, take } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ArtemisNavigationUtilService {
    private onFirstPage = true;

    constructor(private router: Router, private location: Location) {
        router.events
            .pipe(
                filter((e) => e instanceof NavigationEnd),
                skip(1),
                take(1),
            )
            .subscribe(() => {
                this.onFirstPage = false;
            });
    }

    /**
     * Navigates to the last page if possible or to the fallback url if not
     * @param fallbackUrl Url to navigate to if current page is first navigation
     */
    navigateBack(fallbackUrl: string[]) {
        if (!this.onFirstPage) {
            this.location.back();
        } else {
            this.router.navigate(fallbackUrl);
        }
    }

    /**
     * Navigates to the last page if possible or to the fallback url if not. If the optional element is present, it is appended to the fallback url
     * @param fallbackUrl Url to navigate to if current page is first navigation
     * @param optionalLastElement last element of the url or nothing
     */
    navigateBackWithOptional(fallbackUrl: string[], optionalLastElement: string | undefined) {
        if (optionalLastElement) {
            fallbackUrl.push(optionalLastElement);
        }
        this.navigateBack(fallbackUrl);
    }
}

export const navigateToExampleSubmissions = (router: Router, exercise: Exercise): void => {
    setTimeout(() => {
        // If an exercise group is set -> we are in exam mode
        if (exercise.exerciseGroup) {
            router.navigate([
                'course-management',
                exercise.exerciseGroup!.exam!.course!.id!.toString(),
                'exams',
                exercise.exerciseGroup!.exam!.id!.toString(),
                'exercise-groups',
                exercise.exerciseGroup!.id!,
                exercise.type! + '-exercises',
                exercise.id,
                'example-submissions',
            ]);
            return;
        }

        router.navigate(['course-management', exercise.course!.id!, exercise.type! + '-exercises', exercise.id, 'example-submissions']);
    }, 1000);
};

/**
 * Revert to the previous state, equivalent with pressing the back button on your browser
 * Returns to the detail page if there is no previous state and we edited an existing exercise
 * Returns to the overview page if there is no previous state and we created a new exercise
 * Returns to the exercise group page if we are in exam mode
 */
export const navigateBackFromExerciseUpdate = (router: Router, exercise: Exercise): void => {
    if (window.history.length > 1) {
        window.history.back();
        return;
    }

    // If an exercise group is set we are in exam mode
    if (exercise.exerciseGroup) {
        router.navigate(['course-management', exercise.exerciseGroup!.exam!.course!.id!.toString(), 'exams', exercise.exerciseGroup!.exam!.id!.toString(), 'exercise-groups']);
        return;
    }

    if (exercise.id) {
        router.navigate(['course-management', exercise.course!.id!.toString(), exercise.type! + '-exercises', exercise.id!.toString()]);
    } else {
        router.navigate(['course-management', exercise.course!.id!.toString(), exercise.type! + '-exercises']);
    }
};

export const getLinkToSubmissionAssessment = (
    exerciseType: ExerciseType,
    courseId: number,
    exerciseId: number,
    submissionId: number | 'new',
    examId: number,
    exerciseGroupId: number,
    resultId?: number,
): string[] => {
    if (examId > 0) {
        const route = [
            '/course-management',
            courseId.toString(),
            'exams',
            examId.toString(),
            'exercise-groups',
            exerciseGroupId.toString(),
            exerciseType + '-exercises',
            exerciseId.toString(),
            'submissions',
            submissionId.toString(),
            'assessment',
        ];
        if (resultId) {
            route[route.length - 1] += 's';
            route.push(resultId.toString());
        }
        return route;
    } else {
        return ['/course-management', courseId.toString(), exerciseType + '-exercises', exerciseId.toString(), 'submissions', submissionId.toString(), 'assessment'];
    }
};

export const getExerciseDashboardLink = (courseId: number, exerciseId: number, examId = 0, isTestRun = false): string[] => {
    if (isTestRun) {
        return ['/course-management', courseId.toString(), 'exams', examId.toString(), 'test-runs', 'assess'];
    }

    return examId > 0
        ? ['/course-management', courseId.toString(), 'exams', examId.toString(), 'assessment-dashboard', exerciseId.toString()]
        : ['/course-management', courseId.toString(), 'assessment-dashboard', exerciseId.toString()];
};

export const getExerciseSubmissionsLink = (exerciseType: ExerciseType, courseId: number, exerciseId: number, examId = 0, exerciseGroupId = 0): string[] => {
    if (examId > 0 && exerciseGroupId > 0) {
        return [
            '/course-management',
            courseId.toString(),
            'exams',
            examId.toString(),
            'exercise-groups',
            exerciseGroupId.toString(),
            exerciseType + '-exercises',
            exerciseId.toString(),
            'submissions',
        ];
    }

    return ['/course-management', courseId.toString(), exerciseType + '-exercises', exerciseId.toString(), 'submissions'];
};
