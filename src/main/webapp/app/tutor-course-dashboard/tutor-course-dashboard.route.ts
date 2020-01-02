import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';

export const tutorCourseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/tutor-dashboard',
        component: TutorCourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.tutorCourseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
