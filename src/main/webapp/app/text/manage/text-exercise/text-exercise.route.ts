import { textExerciseUpdateRoute } from 'app/text/manage/text-exercise/text-exercise-update.route';
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';

export const textExerciseRoute: Routes = [
    textExerciseUpdateRoute('text-exercises/new'),
    {
        path: 'text-exercises/:exerciseId',
        loadChildren: () => import('app/text/manage/assess/text-submission-assessment.route').then((m) => m.textSubmissionAssessmentRoutes),
    },
    textExerciseUpdateRoute('text-exercises/:exerciseId/edit'),
    textExerciseUpdateRoute('text-exercises/:exerciseId/import', 'artemisApp.textExercise.home.importLabel'),
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
