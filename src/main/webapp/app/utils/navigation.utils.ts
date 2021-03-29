import { Router } from '@angular/router';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

export const navigateBack = (router: Router, fallbackUrl: string[]): void => {
    if (window.history.length > 1) {
        window.history.back();
    } else {
        router.navigate(fallbackUrl);
    }
};

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
    // Special case: If we're dealing with programming exercises use 'code-editor' instead of 'submissions'
    const submissionsURL = exerciseType === ExerciseType.PROGRAMMING ? 'code-editor' : 'submissions';

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
            submissionsURL,
            submissionId.toString(),
            'assessment',
        ];
        if (resultId) {
            route[route.length - 1] += 's';
            route.push(resultId.toString());
        }
        return route;
    } else {
        return ['/course-management', courseId.toString(), exerciseType + '-exercises', exerciseId.toString(), submissionsURL, submissionId.toString(), 'assessment'];
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

export const getExerciseSubmissionsLink = (exerciseType: ExerciseType, courseId: number, exerciseId: number, examId: number, exerciseGroupId: number): string[] => {
    if (examId > 0) {
        return [
            '/course-management',
            courseId.toString(),
            'exams',
            examId.toString(),
            'exercise-groups',
            exerciseGroupId.toString(),
            exerciseType + '-exercises',
            exerciseId.toString(),
            'assessment',
        ];
    }

    return ['/course-management', courseId.toString(), exerciseType + '-exercises', exerciseId.toString(), 'assessment'];
};
