import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Routes } from '@angular/router';
import { ModelingSubmissionComponent } from './modeling-submission.component';

export const modelingSubmissionRoute: Routes = [
    {
        path: 'modeling-submission/:participationId', // TODO CZ: change path to include submission id?
        component: ModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
