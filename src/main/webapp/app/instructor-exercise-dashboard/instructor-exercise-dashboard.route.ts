import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';

export const instructorExerciseDashboardRoute: Routes = [
    {
        path: 'course/:courseId/exercise/:exerciseId/instructor-dashboard',
        component: InstructorExerciseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'arTeMiSApp.instructorExerciseDashboard.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
