import { RouterModule, Routes } from '@angular/router';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ParticipationSubmissionDeletePopupComponent } from 'app/exercises/shared/participation-submission/participation-submission-delete-dialog.component';
import { NgModule } from '@angular/core';

const participationSubmissionRoutes: Routes = [
    {
        path: 'course-management/:courseId/exercises/:exerciseId/participations/:participationId/submissions',
        component: ParticipationSubmissionComponent,
        data: {
            authorities: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'],
            pageTitle: 'artemisApp.participation.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const participationSubmissionPopupRoutes: Routes = [
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

@NgModule({
    imports: [RouterModule.forChild([...participationSubmissionRoutes, ...participationSubmissionPopupRoutes])],
    exports: [RouterModule],
})
export class ArtemisParticipationSubmissionRoutingModule {}
