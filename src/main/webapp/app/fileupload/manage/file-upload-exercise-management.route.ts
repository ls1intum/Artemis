import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

import { FileUploadExerciseManagementResolve } from 'app/fileupload/manage/file-upload-exercise-management-resolve.service';

export const routes: Routes = [
    {
        path: 'file-upload-exercises/new',
        loadComponent: () => import('app/fileupload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
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
        path: 'file-upload-exercises/:exerciseId/import',
        loadComponent: () => import('app/fileupload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
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
        path: 'file-upload-exercises/:exerciseId/edit',
        loadComponent: () => import('app/fileupload/manage/file-upload-exercise-update.component').then((m) => m.FileUploadExerciseUpdateComponent),
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
        path: 'file-upload-exercises/:exerciseId',
        loadComponent: () => import('./file-upload-exercise-detail.component').then((m) => m.FileUploadExerciseDetailComponent),
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            fileUploadExercise: FileUploadExerciseManagementResolve,
        },
        data: {
            authorities: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercises/:exerciseId/submissions/:submissionId',
        loadChildren: () => import('app/fileupload/manage/assess/file-upload-assessment.route').then((m) => m.routes),
    },
];
