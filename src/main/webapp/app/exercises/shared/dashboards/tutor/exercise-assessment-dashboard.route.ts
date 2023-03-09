import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { isOrion } from 'app/shared/orion/orion';
import { ExerciseAssessmentDashboardComponent } from './exercise-assessment-dashboard.component';

export const exerciseAssessmentDashboardRoute: Routes = [
    {
        path: ':courseId/assessment-dashboard/:exerciseId',
        component: !isOrion ? ExerciseAssessmentDashboardComponent : OrionExerciseAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.exerciseAssessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
