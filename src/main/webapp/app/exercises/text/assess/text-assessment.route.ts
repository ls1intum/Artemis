import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';

export const textAssessmentRoutes: Routes = [
    {
        path: ':courseId/text-exercises/:exerciseId/assessment',
        component: TextAssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
