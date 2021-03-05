import { Routes } from '@angular/router';
import { ExerciseAssessmentDashboardComponent } from './exercise-assessment-dashboard.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const exerciseAssessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard/:exerciseId',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
