import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/foundation/constants/authority.constants';
import { PendingChangesGuard } from 'app/foundation/guard/pending-changes.guard';

/** Both entry points expose the same editor states; only the surrounding page title differs. */
export function modelingSubmissionRoutes(pageTitle: string): Routes {
    return ['participate/:participationId', 'participate/:participationId/submission/:submissionId', 'participate/:participationId/submission/:submissionId/result/:resultId'].map(
        (path) => ({
            path,
            loadComponent: () => import('./modeling-submission/modeling-submission.component').then((m) => m.ModelingSubmissionComponent),
            data: { authorities: IS_AT_LEAST_STUDENT, pageTitle },
            canActivate: [UserRouteAccessService],
            canDeactivate: [PendingChangesGuard],
        }),
    );
}
