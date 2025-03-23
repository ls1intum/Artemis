import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { of } from 'rxjs';

import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextAssessmentService } from 'app/text/manage/assess/text-assessment.service';
import { TextSubmissionService } from 'app/text/overview/text-submission.service';
import { catchError, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<StudentParticipation | undefined> {
    private textSubmissionService = inject(TextSubmissionService);

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
    private textAssessmentService = inject(TextAssessmentService);

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
