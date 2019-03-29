import { PendingChangesGuard } from '../shared';
import { UserRouteAccessService } from '../core';
import { Routes } from '@angular/router';
import { ModelingSubmissionComponent } from './modeling-submission.component';

export const modelingSubmissionRoute: Routes = [
    {
        path: 'modeling-submission/:participationId', // TODO CZ: change path to include submission id?
        component: ModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];
