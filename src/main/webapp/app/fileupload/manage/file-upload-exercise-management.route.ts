import { fileUploadExerciseUpdateRoute } from 'app/fileupload/manage/file-upload-exercise-update.route';
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';

import { FileUploadExerciseManagementResolve } from 'app/fileupload/manage/services/file-upload-exercise-management-resolve.service';

export const routes: Routes = [
    fileUploadExerciseUpdateRoute('file-upload-exercises/new'),
    fileUploadExerciseUpdateRoute('file-upload-exercises/:exerciseId/import', 'artemisApp.fileUploadExercise.home.importLabel'),

    fileUploadExerciseUpdateRoute('file-upload-exercises/:exerciseId/edit'),
    {
        path: 'file-upload-exercises/:exerciseId',
        loadComponent: () => import('./exercise-details/file-upload-exercise-detail.component').then((m) => m.FileUploadExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
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
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercises/:exerciseId/submissions/:submissionId',
        loadChildren: () => import('app/fileupload/manage/assess/file-upload-assessment.route').then((m) => m.routes),
    },
];
