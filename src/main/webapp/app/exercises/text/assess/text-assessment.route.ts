import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const textAssessmentRoutes: Routes = [
    {
        path: ':courseId/text-exercises/:exerciseId/assessment',
        component: TextAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
