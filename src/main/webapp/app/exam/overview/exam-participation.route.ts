import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';

export const examParticipationRoute: Routes = [
    {
        path: 'overview/grading-key',
        loadComponent: () => import('app/assessment/manage/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'overview/bonus-grading-key',
        loadComponent: () => import('app/assessment/manage/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.exam.title',
            forBonus: true,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'test-exam/:studentExamId',
        loadComponent: () => import('app/exam/overview/exam-participation/exam-participation.component').then((m) => m.ExamParticipationComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercises/:exerciseId/example-solution',
        loadComponent: () => import('app/exercise/example-solution/example-solution.component').then((m) => m.ExampleSolutionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
