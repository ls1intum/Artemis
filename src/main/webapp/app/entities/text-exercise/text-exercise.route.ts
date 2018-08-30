import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { TextExerciseComponent } from './text-exercise.component';
import { TextExerciseDetailComponent } from './text-exercise-detail.component';
import { TextExercisePopupComponent } from './text-exercise-dialog.component';
import { TextExerciseDeletePopupComponent } from './text-exercise-delete-dialog.component';

export const textExerciseRoute: Routes = [
    {
        path: 'text-exercise',
        component: TextExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'text-exercise/:id',
        component: TextExerciseDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const textExercisePopupRoute: Routes = [
    {
        path: 'text-exercise-new',
        component: TextExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'text-exercise/:id/edit',
        component: TextExercisePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'text-exercise/:id/delete',
        component: TextExerciseDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
