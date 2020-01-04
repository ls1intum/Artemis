import { Routes } from '@angular/router';

import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const tutorExerciseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/exercise/:exerciseId/tutor-dashboard',
        component: TutorExerciseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.tutorExerciseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
