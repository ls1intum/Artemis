import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { ModelingAssessmentConflictComponent } from 'app/modeling-assessment-conflict/modeling-assessment-conflict.component';
import { InitialConflictResolutionComponent } from 'app/modeling-assessment-conflict/initial-conflict-resolution/initial-conflict-resolution.component';
import { EscalatedConflictResolutionComponent } from 'app/modeling-assessment-conflict/escalated-conflict-resolution/escalated-conflict-resolution.component';
import { InstructorConflictResolutionComponent } from 'app/modeling-assessment-conflict/instructor-conflict-resolution/instructor-conflict-resolution.component';

export const modelingAssessmentConflictRoutes: Routes = [
    {
        path: 'modeling-exercise/:exerciseId/submissions/:submissionId/assessment/conflict',
        component: InitialConflictResolutionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'modelingAssessmentConflict.conflictResolution',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercise/:exerciseId/results/:resultId/conflict',
        component: EscalatedConflictResolutionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'modelingAssessmentConflict.conflictResolution',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'modeling-exercise/:exerciseId/conflicts/:conflictId',
        component: InstructorConflictResolutionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR'],
            pageTitle: 'modelingAssessmentConflict.conflictResolution',
        },
        canActivate: [UserRouteAccessService],
    },
];
