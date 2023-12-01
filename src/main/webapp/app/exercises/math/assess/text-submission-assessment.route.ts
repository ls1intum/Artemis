import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Routes } from '@angular/router';
import { TextSubmission } from 'app/entities/text-submission.model';
import { of } from 'rxjs';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextSubmissionAssessmentComponent } from './text-submission-assessment.component';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { catchError, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<StudentParticipation | undefined> {
    constructor(private textSubmissionService: TextSubmissionService) {}

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentService.
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const exerciseId = Number(route.paramMap.get('exerciseId'));
        const correctionRound = Number(route.queryParamMap.get('correction-round'));
        if (exerciseId) {
            return this.textSubmissionService
                .getSubmissionWithoutAssessment(exerciseId, 'lock', correctionRound)
                .pipe(map((submission?: TextSubmission) => <StudentParticipation | undefined>submission?.participation))
                .pipe(catchError(() => of(undefined)));
        }
        return of(undefined);
    }
}

@Injectable({ providedIn: 'root' })
export class StudentParticipationResolver implements Resolve<StudentParticipation | undefined> {
    constructor(private textAssessmentService: TextAssessmentService) {}

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentService.
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const submissionId = Number(route.paramMap.get('submissionId'));
        const correctionRound = Number(route.queryParamMap.get('correction-round'));
        const resultId = Number(route.paramMap.get('resultId'));
        if (resultId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, undefined, resultId).pipe(catchError(() => of(undefined)));
        }
        if (submissionId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, correctionRound).pipe(catchError(() => of(undefined)));
        }
        return of(undefined);
    }
}

export const NEW_ASSESSMENT_PATH = 'submissions/new/assessment';
export const textSubmissionAssessmentRoutes: Routes = [
    {
        path: NEW_ASSESSMENT_PATH,
        component: TextSubmissionAssessmentComponent,
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
        path: 'submissions/:submissionId/assessment',
        component: TextSubmissionAssessmentComponent,
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
        path: 'submissions/:submissionId/assessments/:resultId',
        component: TextSubmissionAssessmentComponent,
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
