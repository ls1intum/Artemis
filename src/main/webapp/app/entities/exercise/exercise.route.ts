import { Routes } from '@angular/router';
import { UserRouteAccessService } from '../../core';
import { ExerciseLtiConfigurationPopupComponent } from './exercise-lti-configuration-dialog.component';

export const exercisePopupRoute: Routes = [
    {
        path: 'exercise/:id/lti-configuration',
        component: ExerciseLtiConfigurationPopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
