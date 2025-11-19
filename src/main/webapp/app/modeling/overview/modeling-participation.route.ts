import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Routes } from '@angular/router';

import { IS_USER } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
        data: {
            authorities: IS_USER,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'participate/:participationId/submission/:submissionId',
        loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
        data: {
            authorities: IS_USER,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
