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

import { parseCorrectionRound } from 'app/assessment/shared/util/correction-round.util';

/**
 * What the resolvers below hand to the assessment page. Both swallow load errors so that the page can render instead of
 * the navigation failing, so a missing participation alone does not say why: it means either "there is nothing to
 * assess" or "the exam is not over yet". Only the latter comes with a reason, and the page needs to tell them apart —
 * otherwise it would claim that the submission was not found while it exists and is simply still being worked on.
 */
export interface TextAssessmentRouteData {
    participation?: StudentParticipation;
    assessmentNotPossibleYet?: AssessmentNotPossibleYetState;

    /**
     * The correction round the participation above was loaded for. The page indexes the results of the submission by the
     * round, so it must use the very round that was requested: deriving it from the URL a second time in the component
     * would be a second fallback rule, and a page that keeps a round the resolver did not load for picks the result of a
     * round that is not there (#13396).
     */
    correctionRound: number;
}

/**
 * Turns a failed load into the data the assessment page renders: the "assessment is not possible yet" 403 becomes the
 * reason it explains, every other error becomes the empty state it already handled before.
 *
 * @param error           the failed response of the endpoint that opens the assessment
 * @param correctionRound the round the failed load was for
 * @returns the resolved route data for that error
 */
function routeDataForError(error: HttpErrorResponse, correctionRound: number): Observable<TextAssessmentRouteData> {
    return of({ assessmentNotPossibleYet: getAssessmentNotPossibleYetState(error), correctionRound });
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
        // The round the page works on is decided here, once, and handed on below. An absent or unusable value must not
        // reach the server as NaN, a fraction or a negative round.
        const correctionRound = parseCorrectionRound(route.queryParamMap.get('correction-round'));
        if (exerciseId) {
            return this.textSubmissionService.getSubmissionWithoutAssessment(exerciseId, 'lock', correctionRound).pipe(
                map((submission?: TextSubmission) => ({ participation: submission?.participation, correctionRound })),
                catchError((error: HttpErrorResponse) => routeDataForError(error, correctionRound)),
            );
        }
        return of({ correctionRound });
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
        // The round the page works on is decided here, once, and handed on below. An absent or unusable value must not
        // reach the server as NaN, a fraction or a negative round.
        const correctionRound = parseCorrectionRound(route.queryParamMap.get('correction-round'));
        const resultId = Number(route.paramMap.get('resultId'));
        if (resultId) {
            // A named result identifies its round by itself, so the page derives the round from the result it got back
            // rather than from the parameter. The round travels along anyway to keep the route data one shape.
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, undefined, resultId).pipe(
                map((participation) => ({ participation, correctionRound })),
                catchError((error: HttpErrorResponse) => routeDataForError(error, correctionRound)),
            );
        }
        if (submissionId) {
            return this.textAssessmentService.getFeedbackDataForExerciseSubmission(submissionId, correctionRound).pipe(
                map((participation) => ({ participation, correctionRound })),
                catchError((error: HttpErrorResponse) => routeDataForError(error, correctionRound)),
            );
        }
        return of({ correctionRound });
    }
}
