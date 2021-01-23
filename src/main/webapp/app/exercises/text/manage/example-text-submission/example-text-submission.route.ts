import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleTextSubmissionComponent } from './example-text-submission.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const exampleTextSubmissionRoute: Routes = [
    {
        path: '',
        component: ExampleTextSubmissionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
