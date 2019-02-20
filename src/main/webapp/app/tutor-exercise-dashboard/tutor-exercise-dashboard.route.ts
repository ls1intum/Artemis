import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';

export const tutorExerciseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/exercise/:exerciseId/tutor-dashboard',
        component: TutorExerciseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.tutorExerciseDashboard.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
