import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ParticipationSubmissionComponent } from 'app/entities/participation-submission/participation-submission.component';
import { ParticipationSubmissionDeletePopupComponent } from 'app/entities/participation-submission/participation-submission-delete-dialog.component';

export const participationSubmissionRoute: Routes = [
    {
        path: 'participation/:participationId/submissions',
        component: ParticipationSubmissionComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export const participationSubmissionPopupRoute: Routes = [
    {
        path: 'participation/:participationId/submission/:submissionId/delete',
        component: ParticipationSubmissionDeletePopupComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup',
    },
];
