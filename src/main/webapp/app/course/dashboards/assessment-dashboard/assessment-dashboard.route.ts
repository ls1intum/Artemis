import { Routes } from '@angular/router';

import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { Authority } from 'app/shared/constants/authority.constants';

export const assessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard',
        component: AssessmentDashboardComponent,
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
