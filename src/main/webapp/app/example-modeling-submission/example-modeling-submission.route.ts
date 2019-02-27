import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { ExampleModelingSubmissionComponent } from './example-modeling-submission.component';

export const exampleModelingSubmissionRoute: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/example-submission/:exampleSubmissionId',
        component: ExampleModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.exampleSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
