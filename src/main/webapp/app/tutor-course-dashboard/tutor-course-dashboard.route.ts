import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';

export const tutorCourseDashboardRoute: Routes = [
    {
        path: 'tutor-course-dashboard',
        component: TutorCourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.tutorCourseDashboard.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
