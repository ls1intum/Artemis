import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentLocksComponent } from './assessment-locks.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

export const assessmentLocksRoute: Routes = [
    {
        path: ':courseId/exams/:examId/assessment-locks',
        component: AssessmentLocksComponent,
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
        path: ':courseId/assessment-locks',
        component: AssessmentLocksComponent,
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
