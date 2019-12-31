import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';

export const instructorCourseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/instructor-dashboard',
        component: InstructorCourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.instructorCourseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
