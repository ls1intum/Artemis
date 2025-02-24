import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        loadComponent: () => import('app/exercises/file-upload/participate/file-upload-submission.component').then((m) => m.FileUploadSubmissionComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
