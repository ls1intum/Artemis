import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const examParticipationRoute: Routes = [
    {
        path: 'overview/grading-key',
        loadComponent: () => import('app/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'overview/bonus-grading-key',
        loadComponent: () => import('app/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
            forBonus: true,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'test-exam/:studentExamId',
        loadComponent: () => import('app/exam/participate/exam-participation.component').then((m) => m.ExamParticipationComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercises/:exerciseId/example-solution',
        loadComponent: () => import('app/exercises/shared/example-solution/example-solution.component').then((m) => m.ExampleSolutionComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
