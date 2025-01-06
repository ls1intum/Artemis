import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

import { ModelingExerciseResolver } from 'app/exercises/modeling/manage/modeling-exercise-resolver.service';

export const routes: Routes = [
    {
        path: ':courseId/modeling-exercises/new',
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        path: ':courseId/modeling-exercises/:exerciseId/edit',
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        path: ':courseId/modeling-exercises/:exerciseId/import',
        loadComponent: () => import('app/exercises/modeling/manage/modeling-exercise-update.component').then((m) => m.ModelingExerciseUpdateComponent),
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
        path: ':courseId/modeling-exercises/:exerciseId',
        loadComponent: () => import('./modeling-exercise-detail.component').then((m) => m.ModelingExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/modeling-exercises/:exerciseId/example-submissions',
        loadComponent: () => import('app/exercises/shared/example-submission/example-submissions.component').then((m) => m.ExampleSubmissionsComponent),
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
        path: ':courseId/modeling-exercises/:exerciseId/plagiarism',
        loadComponent: () => import('app/exercises/shared/plagiarism/plagiarism-inspector/plagiarism-inspector.component').then((m) => m.PlagiarismInspectorComponent),
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.plagiarism.plagiarismDetection',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/modeling-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/modeling-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercises/shared/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ModelingExerciseResolver,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisModelingExerciseRoutingModule {}
