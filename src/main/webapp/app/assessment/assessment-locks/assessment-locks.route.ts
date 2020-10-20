import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentLocksComponent } from './assessment-locks.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const assessmentLocksRoute: Routes = [
    {
        path: ':courseId/assessment-locks',
        component: AssessmentLocksComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.assessment.locks.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
