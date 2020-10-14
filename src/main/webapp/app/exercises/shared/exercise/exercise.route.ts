import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExerciseLtiConfigurationComponent } from './exercise-lti-configuration.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const exercisePopupRoute: Routes = [
    {
        path: ':courseId/exercises/:exerciseId/lti-configuration',
        component: ExerciseLtiConfigurationComponent,
        data: {
            authorities: [Authority.TA, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
