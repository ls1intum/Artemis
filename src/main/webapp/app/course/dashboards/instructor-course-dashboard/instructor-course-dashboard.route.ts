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
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.instructorCourseDashboard.title', path: 'instructor-dashboard' },
            ],
            pageTitle: 'artemisApp.instructorCourseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
