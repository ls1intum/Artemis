import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { NgModule } from '@angular/core';

import { Authority } from 'app/shared/constants/authority.constants';

import { FileUploadExerciseManagementResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management-resolve.service';

const routes: Routes = [
    {
        path: ':courseId/file-upload-exercises/new',
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/import',
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.importLabel',
        },
        canActivate: [UserRouteAccessService],
    },

    {
        path: ':courseId/file-upload-exercises/:exerciseId/edit',
        loadComponent: () => import('app/exercises/file-upload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId',
        loadComponent: () => import('./file-upload-exercise-detail.component').then((m) => m.FileUploadExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises',
        redirectTo: ':courseId/exercises',
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercises/shared/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
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
export class ArtemisFileUploadExerciseManagementRoutingModule {}
