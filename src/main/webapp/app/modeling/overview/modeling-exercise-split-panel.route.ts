import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const modelingExerciseSplitPanelRoute: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'participate/:participationId/submission/:submissionId',
        loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'participate/:participationId/submission/:submissionId/result/:resultId',
        loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
