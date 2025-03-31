import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/core/course/manage/course-management-resolve.service';

export const assessmentLocksRoute: Routes = [
    {
        path: 'exams/:examId/assessment-locks',
        loadComponent: () => import('./assessment-locks.component').then((m) => m.AssessmentLocksComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessment.locks.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'assessment-locks',
        loadComponent: () => import('./assessment-locks.component').then((m) => m.AssessmentLocksComponent),
        resolve: {
            course: CourseManagementResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessment.locks.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
