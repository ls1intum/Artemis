import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExerciseUpdateComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-update.component';

export const fileUploadExerciseRoute: Routes = [
    {
        path: 'file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercise/:exerciseId',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise/new',
        component: FileUploadExerciseUpdateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercise/:exerciseId/edit',
        component: FileUploadExerciseUpdateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/file-upload-exercise/:id',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
