import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_EDITOR, IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';
import { ProofExerciseResolver } from 'app/proof/manage/service/proof-exercise-resolver.service';
import { ProofSubmissionAssessmentResolverService } from 'app/proof/manage/assess/proof-submission-assessment-resolver.service';

export const proofExerciseRoute: Routes = [
    {
        path: 'proof-exercises/:exerciseId/submissions/:submissionId/assessment',
        loadComponent: () => import('./manage/assess/proof-submission-assessment.component').then((m) => m.ProofSubmissionAssessmentComponent),
        resolve: {
            proofSubmission: ProofSubmissionAssessmentResolverService,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises',
        loadComponent: () => import('./manage/exercise/proof-exercise.component').then((m) => m.ProofExerciseComponent),
        resolve: {
            proofExercise: ProofExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises/new',
        loadComponent: () => import('./manage/update/proof-exercise-update.component').then((m) => m.ProofExerciseUpdateComponent),
        resolve: {
            proofExercise: ProofExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises/:exerciseId',
        loadComponent: () => import('./manage/detail/proof-exercise-detail.component').then((m) => m.ProofExerciseDetailComponent),
        resolve: {
            proofExercise: ProofExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises/:exerciseId/edit',
        loadComponent: () => import('./manage/update/proof-exercise-update.component').then((m) => m.ProofExerciseUpdateComponent),
        resolve: {
            proofExercise: ProofExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_EDITOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises/:exerciseId/exercise-statistics',
        loadComponent: () => import('app/exercise/statistics/exercise-statistics.component').then((m) => m.ExerciseStatisticsComponent),
        resolve: {
            exercise: ProofExerciseResolver,
        },
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'exercise-statistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'proof-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        loadComponent: () => import('./manage/assess/proof-submission-assessment.component').then((m) => m.ProofSubmissionAssessmentComponent),
        resolve: {
            proofSubmission: ProofSubmissionAssessmentResolverService,
        },
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.proofExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
