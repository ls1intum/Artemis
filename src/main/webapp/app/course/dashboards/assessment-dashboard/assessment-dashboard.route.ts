import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const assessmentDashboardRoute: Routes = [
    {
        path: ':courseId/tutor-dashboard',
        component: AssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
