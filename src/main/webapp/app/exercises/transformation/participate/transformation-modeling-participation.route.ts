import { NgModule } from '@angular/core';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { TransformationModelingSubmissionComponent } from './transformation-modeling-submission.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        component: TransformationModelingSubmissionComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisTransformationModelingParticipationRoutingModule {}
