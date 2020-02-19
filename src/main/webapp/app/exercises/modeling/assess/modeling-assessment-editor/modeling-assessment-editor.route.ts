import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentConflictComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-conflict/modeling-assessment-conflict.component';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard/modeling-assessment-dashboard.component';

export const modelingAssessmentRoutes: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment/conflict',
        component: ModelingAssessmentConflictComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'course/:courseId/exercise/:exerciseId/assessment',
        component: ModelingAssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
