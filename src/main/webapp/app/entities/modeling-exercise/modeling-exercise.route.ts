import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ModelingExerciseComponent } from './modeling-exercise.component';
import { ModelingExerciseDetailComponent } from './modeling-exercise-detail.component';
import { ModelingExercisePopupComponent } from './modeling-exercise-dialog.component';
import { ModelingExerciseDeletePopupComponent } from './modeling-exercise-delete-dialog.component';

export const modelingExerciseRoute: Routes = [
    {
        path: 'modeling-exercise',
        component: ModelingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'modeling-exercise/:id',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'course/:courseId/modeling-exercise',
        component: ModelingExerciseComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'course/:courseId/modeling-exercise/:id',
        component: ModelingExerciseDetailComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const modelingExercisePopupRoute: Routes = [
    {
        path: 'course/:courseId/modeling-exercise-new',
        component: ModelingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'modeling-exercise/:id/edit',
        component: ModelingExercisePopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'modeling-exercise/:id/delete',
        component: ModelingExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
