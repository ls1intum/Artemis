import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../core';
import { TextExerciseComponent } from './text-exercise.component';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExerciseUpdateComponent } from './text-exercise-update.component';
import { TextExerciseDeletePopupComponent } from './text-exercise-delete-dialog.component';

export const textExerciseRoute: Routes = [
    {
        path: 'text-exercise/:id',
        component: TextExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise',
        component: TextExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise/:id',
        component: TextExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/text-exercise/new',
        component: TextExerciseUpdateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'text-exercise/:exerciseId/edit',
        component: TextExerciseUpdateComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const textExercisePopupRoute: Routes = [
    {
        path: 'text-exercise/:id/delete',
        component: TextExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
