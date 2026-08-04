import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Observable, of } from 'rxjs';

import { AssessmentNotPossibleYetState, getAssessmentNotPossibleYetState } from 'app/assessment/shared/util/assessment-availability.util';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { catchError, map } from 'rxjs/operators';

import { correctionRoundToLoad } from 'app/assessment/shared/util/correction-round.util';

/**
 * What the resolvers below hand to the assessment page. Both swallow load errors so that the page can render instead of
 * the navigation failing, so a missing participation alone does not say why: it means either "there is nothing to
 * assess" or "the exam is not over yet". Only the latter comes with a reason, and the page needs to tell them apart —
 * otherwise it would claim that the submission was not found while it exists and is simply still being worked on.
 */
export interface TextAssessmentRouteData {
    participation?: StudentParticipation;
    assessmentNotPossibleYet?: AssessmentNotPossibleYetState;
}

/**
 * Turns a failed load into the data the assessment page renders: the "assessment is not possible yet" 403 becomes the
 * reason it explains, every other error becomes the empty state it already handled before.
 *
 * @param error the failed response of the endpoint that opens the assessment
 * @returns the resolved route data for that error
 */
function routeDataForError(error: HttpErrorResponse): Observable<TextAssessmentRouteData> {
    return of({ assessmentNotPossibleYet: getAssessmentNotPossibleYetState(error) });
}

@Injectable({ providedIn: 'root' })
export class NewStudentParticipationResolver implements Resolve<TextAssessmentRouteData> {
    private textSubmissionService = inject(TextSubmissionService);

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentService.
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot): Observable<TextAssessmentRouteData> {
        const exerciseId = Number(route.paramMap.get('exerciseId'));
        // Shared with the assessment editors: an absent or unusable value must not reach the server as NaN, a
        // fraction or a negative round, and it must resolve to the same round the editor will believe it is in.
        const correctionRound = correctionRoundToLoad(route.queryParamMap.get('correction-round'));
        if (exerciseId) {
            return this.textSubmissionService.getSubmissionWithoutAssessment(exerciseId, 'lock', correctionRound).pipe(
                map((submission?: TextSubmission) => ({ participation: submission?.participation })),
                catchError(routeDataForError),
            );
        }
        return of({});
    }
}

@Injectable({ providedIn: 'root' })
export class StudentParticipationResolver implements Resolve<TextAssessmentRouteData> {
    private textAssessmentService = inject(TextAssessmentService);

    /**
     * Resolves the needed StudentParticipations for the TextSubmissionAssessmentComponent using the TextAssessmentService.
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot): Observable<TextAssessmentRouteData> {
        const submissionId = Number(route.paramMap.get('submissionId'));
        // Shared with the assessment editors: an absent or unusable value must not reach the server as NaN, a
        // fraction or a negative round, and it must resolve to the same round the editor will believe it is in.
        const correctionRound = correctionRoundToLoad(route.queryParamMap.get('correction-round'));
        const resultId = Number(route.paramMap.get('resultId'));
        if (resultId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, undefined, resultId).pipe(
                map((participation) => ({ participation })),
                catchError(routeDataForError),
            );
        }
        if (submissionId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, correctionRound).pipe(
                map((participation) => ({ participation })),
                catchError(routeDataForError),
            );
        }
        return of({});
    }
}
