import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseDetailComponent } from './file-upload-exercise-detail.component';
import { FileUploadExercisePopupComponent } from './file-upload-exercise-dialog.component';
import { FileUploadExerciseDeletePopupComponent } from './file-upload-exercise-delete-dialog.component';

export const fileUploadExerciseRoute: Routes = [
    {
        path: 'file-upload-exercise',
        component: FileUploadExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'file-upload-exercise/:id',
        component: FileUploadExerciseDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const fileUploadExercisePopupRoute: Routes = [
    {
        path: 'file-upload-exercise-new',
        component: FileUploadExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'file-upload-exercise/:id/edit',
        component: FileUploadExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'file-upload-exercise/:id/delete',
        component: FileUploadExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.fileUploadExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
