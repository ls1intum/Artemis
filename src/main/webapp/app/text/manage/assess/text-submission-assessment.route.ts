import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
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
            textAssessmentData: NewStudentParticipationResolver,
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
            textAssessmentData: StudentParticipationResolver,
        },
        // The correction round is a query parameter, and the resolver loads the participation of that round, so a round
        // that changes in the URL has to load again. With `paramsChange` the resolver only re-runs for the submission id,
        // which left the page showing the round it was opened with (#13396).
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
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
            textAssessmentData: StudentParticipationResolver,
        },
        // A named result identifies its own round, so this route resolves by the result id and ignores the query
        // parameter. Only a different result has to load again.
        runGuardsAndResolvers: 'paramsChange',
        canActivate: [UserRouteAccessService],
    },
];
