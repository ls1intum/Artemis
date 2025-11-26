import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';

export const gradingSystemRoutes: Routes = [
    {
        path: '',
        redirectTo: 'interval',
        pathMatch: 'full',
    },
    {
        path: 'interval',
        loadComponent: () => import('app/assessment/manage/grading-system/interval-grading-system/interval-grading-system.component').then((m) => m.IntervalGradingSystemComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.gradingSystem.intervalTab.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'detailed',
        loadComponent: () => import('app/assessment/manage/grading-system/detailed-grading-system/detailed-grading-system.component').then((m) => m.DetailedGradingSystemComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.gradingSystem.detailedTab.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
