import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const fileUploadSubmissionRoute: Routes = [
    {
        path: 'file-upload-submission/:participationId',
        component: FileUploadSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
