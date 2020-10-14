import { Routes } from '@angular/router';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const tutorExerciseDashboardRoute: Routes = [
    {
        path: ':courseId/exercises/:exerciseId/tutor-dashboard',
        component: TutorExerciseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.tutorExerciseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },

    {
        path: ':courseId/exercises/:exerciseId/test-run-tutor-dashboard',
        component: TutorExerciseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.tutorExerciseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
