import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { NewStudentParticipationResolver, StudentParticipationResolver } from 'app/text/manage/assess/service/text-submission-assessment-resolve.service';

export const NEW_ASSESSMENT_PATH = 'submissions/new/assessment';
export const textSubmissionAssessmentRoutes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/text/manage/detail/text-exercise-detail.component').then((m) => m.TextExerciseDetailComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: NEW_ASSESSMENT_PATH,
        loadComponent: () => import('./submission-assessment/text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: NewStudentParticipationResolver,
        },
        runGuardsAndResolvers: 'always',
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'submissions/:submissionId/assessment',
        loadComponent: () => import('./submission-assessment/text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: StudentParticipationResolver,
        },
        runGuardsAndResolvers: 'paramsChange',
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('./submission-assessment/text-submission-assessment.component').then((m) => m.TextSubmissionAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.textAssessment.title',
        },
        resolve: {
            studentParticipation: StudentParticipationResolver,
        },
        runGuardsAndResolvers: 'paramsChange',
        canActivate: [UserRouteAccessService],
    },
];
