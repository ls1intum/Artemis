import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { NewStudentParticipationResolver, StudentParticipationResolver } from 'app/exercises/text/assess/text-submission-assessment-resolve.service';

export const NEW_ASSESSMENT_PATH = 'new/assessment';
export const textSubmissionAssessmentRoutes: Routes = [
    {
        path: NEW_ASSESSMENT_PATH,
        loadComponent: () => import('./text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: NewStudentParticipationResolver,
        },
        runGuardsAndResolvers: 'always',
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':submissionId/assessment',
        loadComponent: () => import('./text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: StudentParticipationResolver,
        },
        runGuardsAndResolvers: 'paramsChange',
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':submissionId/assessments/:resultId',
        loadComponent: () => import('./text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: StudentParticipationResolver,
        },
        runGuardsAndResolvers: 'paramsChange',
        canActivate: [UserRouteAccessService],
    },
];
