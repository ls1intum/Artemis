import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseResolve } from 'app/course/manage/course-management.route';

export const instructorCourseDashboardRoute: Routes = [
    {
        path: ':courseId/instructor-dashboard',
        resolve: {
            course: CourseResolve,
        },
        component: InstructorCourseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.instructorCourseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
