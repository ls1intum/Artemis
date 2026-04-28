import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const fileUploadParticipationRoute: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('./file-upload-submission/file-upload-submission.component').then((m) => m.FileUploadSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
