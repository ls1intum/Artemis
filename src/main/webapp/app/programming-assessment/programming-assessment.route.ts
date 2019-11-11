import { UserRouteAccessService } from 'app/core';
import { Routes } from '@angular/router';
import { ProgrammingAssessmentManualResultComponent } from 'app/programming-assessment/manual-result';

export const programmingAssessmentRoutes: Routes = [
    {
        path: 'programming-exercise/:exerciseId/submissions/:submissionId/assessment',
        component: ProgrammingAssessmentManualResultComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
