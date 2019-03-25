import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';

export const modelingAssessmentRoutes: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment',
        component: ModelingAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    },
];
