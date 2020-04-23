import { NgModule } from '@angular/core';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { ModelingSubmissionComponent } from './modeling-submission.component';

export const routes: Routes = [
    {
        path: 'participate/:participationId',
        component: ModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
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
