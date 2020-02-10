import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleModelingSubmissionComponent } from './example-modeling-submission.component';

export const exampleModelingSubmissionRoute: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/example-submission/:exampleSubmissionId',
        component: ExampleModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
