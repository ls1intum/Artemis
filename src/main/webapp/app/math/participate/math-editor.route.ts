import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';

export const mathEditorRoute: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./math-submission/math-submission.component').then((m) => m.MathSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.mathExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
