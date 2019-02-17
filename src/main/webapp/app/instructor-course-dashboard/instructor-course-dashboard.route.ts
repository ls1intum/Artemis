import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';

export const instructorCourseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/instructor-dashboard',
        component: InstructorCourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'arTeMiSApp.instructorCourseDashboard.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
