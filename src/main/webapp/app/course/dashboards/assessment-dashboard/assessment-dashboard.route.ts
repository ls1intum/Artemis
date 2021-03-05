import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseResolve } from 'app/course/manage/course-management.route';

export const assessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard',
        component: AssessmentDashboardComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.assessmentDashboard.home.title', path: 'assessment-dashboard' },
            ],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
