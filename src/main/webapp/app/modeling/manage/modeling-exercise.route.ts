import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

import { ModelingExerciseResolver } from 'app/modeling/manage/modeling-exercise-resolver.service';

export const routes: Routes = [
    {
        path: 'modeling-exercises/new',
        loadComponent: () => import('app/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/edit',
        loadComponent: () => import('app/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/import',
        loadComponent: () => import('app/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
        resolve: {
            modelingExercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId',
        loadComponent: () => import('./modeling-exercise-detail.component').then((m) => m.ModelingExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercise/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        loadComponent: () => import('app/modeling/manage/example-modeling/example-modeling-submission.component').then((m) => m.ExampleModelingSubmissionComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercises/:exerciseId/submissions/:submissionId',
        loadChildren: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.route').then((m) => m.routes),
    },
];
