import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { MathExerciseResolver } from 'app/math/manage/service/math-exercise-resolver.service';
import { MathSubmissionAssessmentResolverService } from 'app/math/manage/assess/math-submission-assessment-resolver.service';

export const mathExerciseRoute: Routes = [
    {
        path: 'math-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('./manage/assess/math-submission-assessment.component').then((m) => m.MathSubmissionAssessmentComponent),
        resolve: {
            mathSubmission: MathSubmissionAssessmentResolverService,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises',
        loadComponent: () => import('./manage/exercise/math-exercise.component').then((m) => m.MathExerciseComponent),
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises/new',
        loadComponent: () => import('./manage/update/math-exercise-update.component').then((m) => m.MathExerciseUpdateComponent),
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises/:exerciseId',
        loadComponent: () => import('./manage/detail/math-exercise-detail.component').then((m) => m.MathExerciseDetailComponent),
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises/:exerciseId/edit',
        loadComponent: () => import('./manage/update/math-exercise-update.component').then((m) => m.MathExerciseUpdateComponent),
        resolve: {
            mathExercise: MathExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: MathExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'math-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('./manage/assess/math-submission-assessment.component').then((m) => m.MathSubmissionAssessmentComponent),
        resolve: {
            mathSubmission: MathSubmissionAssessmentResolverService,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
