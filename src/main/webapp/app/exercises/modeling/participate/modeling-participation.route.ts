import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ModelingSubmissionComponent } from './modeling-submission.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        component: ModelingSubmissionComponent,
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
export class ArtemisModelingParticipationRoutingModule {}
