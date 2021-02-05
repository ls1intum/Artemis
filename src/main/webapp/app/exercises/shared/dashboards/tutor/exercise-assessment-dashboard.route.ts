import { Routes } from '@angular/router';
import { ExerciseAssessmentDashboardComponent } from './exercise-assessment-dashboard.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const exerciseAssessmentDashboardRoute: Routes = [
    {
        path: ':courseId/exercises/:exerciseId/tutor-dashboard',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },

    {
        path: ':courseId/exercises/:exerciseId/test-run-exercise-assessment-dashboard',
        component: ExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
