import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { ExampleTextSubmissionComponent } from './example-text-submission.component';

export const exampleTextSubmissionRoute: Routes = [
    {
        path: 'text-exercise/:exerciseId/example-submission/:exampleSubmissionId',
        component: ExampleTextSubmissionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.exampleSubmission.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
