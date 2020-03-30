import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload/participate/file-upload-submission.component';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        component: FileUploadSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisFileUploadParticipationRoutingModule {}
