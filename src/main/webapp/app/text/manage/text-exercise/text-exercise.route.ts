import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';

export const textExerciseRoute: Routes = [
    {
        path: 'text-exercises/new',
        loadComponent: () => import('./update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId',
        loadChildren: () => import('app/text/manage/assess/text-submission-assessment.route').then((m) => m.textSubmissionAssessmentRoutes),
    },
    {
        path: 'text-exercises/:exerciseId/edit',
        loadComponent: () => import('./update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId/import',
        loadComponent: () => import('./update/text-exercise-update.component').then((m) => m.TextExerciseUpdateComponent),
        resolve: {
            textExercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.textExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/plagiarism/manage/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercise/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId/iris-settings',
        loadChildren: () => import('app/iris/manage/settings/iris-exercise-settings-update/iris-exercise-settings-update-route').then((m) => m.routes),
    },
    {
        path: 'text-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: TextExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercises/:exerciseId/tutor-effort-statistics',
        loadChildren: () => import('../tutor-effort/tutor-effort-statistics.route').then((m) => m.tutorEffortStatisticsRoute),
    },
    {
        path: 'text-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadChildren: () => import('../example-text-submission/example-text-submission.route').then((m) => m.exampleTextSubmissionRoute),
    },
];
