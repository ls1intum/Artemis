import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const instructorExerciseDashboardRoute: Routes = [
    {
        path: ':courseId/instructor-dashboard/:exerciseId',
        component: InstructorExerciseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.instructorExerciseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
