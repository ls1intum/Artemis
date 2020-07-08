import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentLocksComponent } from './assessment-locks.component';

export const assessmentLocksRoute: Routes = [
    {
        path: ':courseId/assessment-locks',
        component: AssessmentLocksComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.assessment.locks.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
