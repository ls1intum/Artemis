import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment/modeling-assessment-editor.component';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment/modeling-assessment-dashboard.component';

export const modelingAssessmentRoutes: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title',
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
