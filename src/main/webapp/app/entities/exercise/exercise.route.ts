import { Routes } from '@angular/router';
import { UserRouteAccessService } from '../../shared';
import { ExerciseComponent } from './exercise.component';
import { ExerciseLtiConfigurationPopupComponent } from './exercise-lti-configuration-dialog.component';
import { ExerciseResetPopupComponent } from './exercise-reset-dialog.component';

export const exerciseRoute: Routes = [
    {
        path: 'exercise',
        component: ExerciseComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.exercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const exercisePopupRoute: Routes = [
    {
        path: 'exercise/:id/lti-configuration',
        component: ExerciseLtiConfigurationPopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'exercise/:id/reset',
        component: ExerciseResetPopupComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'arTeMiSApp.programmingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
