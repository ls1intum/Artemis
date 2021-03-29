import { RouterModule, Routes } from '@angular/router';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ParticipationSubmissionDeletePopupComponent } from 'app/exercises/shared/participation-submission/participation-submission-delete-dialog.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { exerciseTypes } from 'app/entities/exercise.model';

const participationSubmissionRoutes: Routes = [
    ...exerciseTypes.map((exerciseType) => {
        return {
            path: ':courseId/' + exerciseType + '-exercises/:exerciseId/participations/:participationId/submissions',
            component: ParticipationSubmissionComponent,
            data: {
                authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                pageTitle: 'artemisApp.participation.home.title',
            },
            canActivate: [UserRouteAccessService],
        };
    }),
];

const participationSubmissionPopupRoutes: Routes = [
    {
        path: 'participation/:participationId/submission/:submissionId/delete',
        component: ParticipationSubmissionDeletePopupComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
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
