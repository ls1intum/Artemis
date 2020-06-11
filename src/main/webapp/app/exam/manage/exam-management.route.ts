import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { ExamCoverComponent } from 'app/exam/participate/exam-cover/exam-cover.component';

export const examManagementRoute: Routes = [
    {
        path: '',
        component: ExamCoverComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_MANAGEMENT_ROUTES = [...examManagementRoute];

export const examManagementState: Routes = [
    {
        path: '',
        children: EXAM_MANAGEMENT_ROUTES,
    },
];
