import { Injectable } from '@angular/core';
import { NavigationEnd, Params, Router } from '@angular/router';
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
    navigateBack(fallbackUrl?: (string | number)[]) {
        if (!this.onFirstPage) {
            this.location.back();
        } else if (fallbackUrl) {
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

    /**
     * Navigate to exercise detail page after creating or editing it.
     * @param exercise the updated or created exercise
     */
    navigateForwardFromExerciseUpdateOrCreation(exercise?: Exercise) {
        if (exercise?.exerciseGroup?.exam?.course?.id) {
            // If an exercise group is set we are in exam mode
            this.router.navigate([
                'course-management',
                exercise.exerciseGroup.exam.course.id,
                'exams',
                exercise.exerciseGroup.exam.id!,
                'exercise-groups',
                exercise.exerciseGroup.id!,
                exercise.type! + '-exercises',
                exercise.id,
            ]);
        } else if (exercise?.course?.id) {
            this.router.navigate(['course-management', exercise.course.id, exercise.type! + '-exercises', exercise.id]);
        } else {
            // Fallback
            this.navigateBack();
        }
    }

    /**
     * Navigate to exercise detail page if cancelling the update or creation
     * Either
     * - move back in the history
     * - if in exam mode, go to the exercise group view
     * - go to exercises overview
     * @param exercise the updated or created exercise
     */
    navigateBackFromExerciseUpdate(exercise?: Exercise) {
        let fallback: (string | number)[] | undefined = undefined;
        if (exercise?.exerciseGroup?.exam?.course?.id) {
            // If an exercise group is set we are in exam mode
            fallback = ['/course-management', exercise.exerciseGroup.exam.course.id, 'exams', exercise.exerciseGroup.exam.id!, 'exercise-groups'];
        } else if (exercise?.course?.id) {
            fallback = ['/course-management', exercise.course.id, 'exercises'];
        }
        this.navigateBack(fallback);
    }

    replaceNewWithIdInUrl(url: string, id: number) {
        const newUrl = url.slice(0, -3) + id;
        const regex = /http(s)?:\/\/([a-zA-Z0-9\.\:]*)(?<rest>\/.*)/;
        this.location.go(newUrl.match(regex)!.groups!.rest);
    }

    /**
     * Opens the target page in a new tab
     * @param route the target route
     * @param params the query params of the target route
     */
    routeInNewTab(route: any[], params?: Params): void {
        const url = this.router.serializeUrl(this.router.createUrlTree(route, params));
        window.open(url, '_blank');
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

export const getLinkToSubmissionAssessment = (
    exerciseType: ExerciseType,
    courseId: number,
    exerciseId: number,
    participationId: number | undefined,
    submissionId: number | 'new',
    examId: number,
    exerciseGroupId: number,
    resultId?: number,
): string[] => {
    if (examId > 0) {
        let route;
        if (exerciseType === ExerciseType.TEXT && submissionId !== 'new' && participationId !== undefined) {
            route = [
                '/course-management',
                courseId.toString(),
                'exams',
                examId.toString(),
                'exercise-groups',
                exerciseGroupId.toString(),
                exerciseType + '-exercises',
                exerciseId.toString(),
                'participations',
                participationId.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ];
        } else {
            route = [
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
        }
        if (resultId) {
            route[route.length - 1] += 's';
            route.push(resultId.toString());
        }
        return route;
    } else {
        if (exerciseType === ExerciseType.TEXT && submissionId !== 'new' && participationId !== undefined) {
            return [
                '/course-management',
                courseId.toString(),
                exerciseType + '-exercises',
                exerciseId.toString(),
                'participations',
                participationId.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ];
        } else {
            return ['/course-management', courseId.toString(), exerciseType + '-exercises', exerciseId.toString(), 'submissions', submissionId.toString(), 'assessment'];
        }
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

/**
 * A generic method which navigates into a subpage of an exam exercise
 * @router the router th component uses to navigate into different webpages
 * @subPage the subpage of an exercise which we want to navigate into, e.g. scores
 */
export const navigateToExamExercise = (
    navigationUtilService: ArtemisNavigationUtilService,
    courseId: number,
    examId: number,
    exerciseGroupId: number,
    exerciseType: ExerciseType,
    exerciseId: number,
    subPage: string,
): void => {
    setTimeout(() => {
        navigationUtilService.routeInNewTab(['course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, `${exerciseType}-exercises`, exerciseId, subPage]);
    }, 1000);
};
