import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';

export const assessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard',
        component: CourseManagementTabBarComponent,
        children: [
            {
                path: '',
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
        ],
    },
];
