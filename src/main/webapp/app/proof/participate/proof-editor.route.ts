import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';

export const proofEditorRoute: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./proof-submission/proof-submission.component').then((m) => m.ProofSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
