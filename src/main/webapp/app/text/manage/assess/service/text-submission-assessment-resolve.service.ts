import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { of } from 'rxjs';

import { alertIfAssessmentNotPossibleYet } from 'app/assessment/shared/util/assessment-availability.util';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { catchError, map } from 'rxjs/operators';

/*
 * Both resolvers below swallow load errors so that the assessment page can render its "no submission" state. The global
 * alert interceptor stays silent for the "assessment is not possible yet" 403 (the server marks it as `skipAlert` so the
 * date can be localized in the browser's time zone), so it has to be reported explicitly here, otherwise the tutor
 * would see nothing at all.
 */

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<StudentParticipation | undefined> {
    private textSubmissionService = inject(TextSubmissionService);

    private alertService = inject(AlertService);

    private datePipe = inject(ArtemisDatePipe);

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
                .pipe(map((submission?: TextSubmission) => submission?.participation))
                .pipe(
                    catchError((error: HttpErrorResponse) => {
                        alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe);
                        return of(undefined);
                    }),
                );
        }
        return of(undefined);
    }
}

@Injectable({ providedIn: 'root' })
export class StudentParticipationResolver implements Resolve<StudentParticipation | undefined> {
    private textAssessmentService = inject(TextAssessmentService);

    private alertService = inject(AlertService);

    private datePipe = inject(ArtemisDatePipe);

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentService.
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const submissionId = Number(route.paramMap.get('submissionId'));
        const correctionRound = Number(route.queryParamMap.get('correction-round'));
        const resultId = Number(route.paramMap.get('resultId'));
        const handleError = (error: HttpErrorResponse) => {
            alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe);
            return of(undefined);
        };
        if (resultId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, undefined, resultId).pipe(catchError(handleError));
        }
        if (submissionId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, correctionRound).pipe(catchError(handleError));
        }
        return of(undefined);
    }
}
