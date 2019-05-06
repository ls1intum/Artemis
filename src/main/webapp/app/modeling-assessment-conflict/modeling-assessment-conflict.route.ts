import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ModelingAssessmentConflictComponent } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.component';

export const modelingAssessmentConflictRoutes: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment/conflict',
        component: ModelingAssessmentConflictComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
