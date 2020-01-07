import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';

export const instructorExerciseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/exercise/:exerciseId/instructor-dashboard',
        component: InstructorExerciseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'artemisApp.instructorExerciseDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
