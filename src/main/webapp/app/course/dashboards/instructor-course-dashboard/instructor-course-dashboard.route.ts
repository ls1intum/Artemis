import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const instructorCourseDashboardRoute: Routes = [
    {
        path: ':courseId/instructor-dashboard',
        component: InstructorCourseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.instructorCourseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
