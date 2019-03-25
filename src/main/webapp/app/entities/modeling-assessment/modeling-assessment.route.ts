import { Routes } from '@angular/router';
import { ModelingAssessmentComponent } from 'app/entities/modeling-assessment';
import { UserRouteAccessService } from 'app/core';

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
