import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExerciseLtiConfigurationComponent } from './exercise-lti-configuration.component';

export const exercisePopupRoute: Routes = [
    {
        path: ':courseId/exercises/:exerciseId/lti-configuration',
        component: ExerciseLtiConfigurationComponent,
        data: {
            authorities: ['ROLE_TA', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
