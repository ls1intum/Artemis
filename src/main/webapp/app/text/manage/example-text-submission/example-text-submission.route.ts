import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

export const exampleTextSubmissionRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('./example-text-submission.component').then((m) => m.ExampleTextSubmissionComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.exampleSubmission.home.editor',
        },
        canActivate: [UserRouteAccessService],
    },
];
