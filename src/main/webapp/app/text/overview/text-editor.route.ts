import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_USER } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const textEditorRoute: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./text-editor/text-editor.component').then((m) => m.TextEditorComponent),
        data: {
            authorities: IS_AT_LEAST_USER,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'participate/:participationId/submission/:submissionId',
        loadComponent: () => import('./text-editor/text-editor.component').then((m) => m.TextEditorComponent),
        data: {
            authorities: IS_AT_LEAST_USER,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
