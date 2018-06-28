import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ExerciseComponent } from './exercise.component';
import { ExerciseDetailComponent } from './exercise-detail.component';
import { ExercisePopupComponent } from './exercise-dialog.component';
import { ExerciseDeletePopupComponent } from './exercise-delete-dialog.component';

export const exerciseRoute: Routes = [
    {
        path: 'exercise',
        component: ExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'exercise/:id',
        component: ExerciseDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const exercisePopupRoute: Routes = [
    {
        path: 'exercise-new',
        component: ExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/edit',
        component: ExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/delete',
        component: ExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
