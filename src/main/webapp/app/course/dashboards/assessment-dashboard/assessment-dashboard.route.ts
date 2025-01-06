import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

export const assessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard',
        loadComponent: () => import('app/course/manage/course-management-tab-bar/course-management-tab-bar.component').then((m) => m.CourseManagementTabBarComponent),
        children: [
            {
                path: '',
                loadComponent: () => import('./assessment-dashboard.component').then((m) => m.AssessmentDashboardComponent),
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
