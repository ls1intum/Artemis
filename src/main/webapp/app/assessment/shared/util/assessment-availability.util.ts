import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

/**
 * Error keys the server uses when a tutor tries to assess an exam exercise before assessment is possible, see
 * {@code SubmissionService#checkThatAssessmentIsPossibleElseThrow}. Both translations take a `date` placeholder telling
 * the tutor when they can start.
 */
export const ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING = 'assessmentNotPossibleExamRunning';
export const ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING = 'assessmentNotPossibleTestsPending';

export interface AssessmentNotPossibleYetReason {
    /** The message explaining why assessment is not possible yet. */
    translationKey: string;

    /**
     * The date that message names, matching the one the server sends for the same state. It is also the moment this
     * reason stops applying, or — for a programming exercise whose tests still have to run — changes into the next one.
     */
    date: dayjs.Dayjs;
}

/**
 * Determines why assessment of an exam exercise is not possible yet, based on the dates the assessment dashboard
 * receives with the exercise. Returns undefined for course exercises, for exams that are over, and whenever the server
 * did not send the dates (then the dashboard behaves as before and relies on the server response).
 * <p>
 * The reported key and date mirror what {@code SubmissionService#checkThatAssessmentIsPossibleElseThrow} would answer in
 * the same moment, so the banner here and the alert after a 403 never contradict each other: while the exam is still
 * running we name the exam end date, and only afterwards — programming exercises still waiting for their tests — the
 * later date assessment actually opens at.
 *
 * @param exercise the exam exercise shown on the assessment dashboard
 * @returns the reason assessment is not possible yet, or undefined if it already is
 */
export function getAssessmentNotPossibleYetReason(exercise: Exercise | undefined): AssessmentNotPossibleYetReason | undefined {
    const assessmentPossibleFrom = exercise?.assessmentPossibleFrom;
    if (!assessmentPossibleFrom || !dayjs().isBefore(assessmentPossibleFrom)) {
        return undefined;
    }
    const latestExamEndDate = exercise?.latestExamEndDate;
    if (!latestExamEndDate || dayjs().isBefore(latestExamEndDate)) {
        return { translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, date: latestExamEndDate ?? assessmentPossibleFrom };
    }
    return { translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING}`, date: assessmentPossibleFrom };
}

/**
 * Turns the server's "assessment is not possible yet" 403 into the translation key and parameters needed to display it.
 * The server sends the date as an ISO string and marks the response as `skipAlert`, so that the date can be rendered in
 * the browser's locale and time zone here instead of being shown raw by the global alert interceptor.
 *
 * @param error the failed response of an endpoint that opens an assessment
 * @param datePipe used to localize the date the server sent
 * @returns the translation key and parameters, or undefined if the error is a different one
 */
export function getAssessmentNotPossibleYetAlert(error: HttpErrorResponse, datePipe: ArtemisDatePipe): { translationKey: string; params: { date: string } } | undefined {
    const errorKey = error?.error?.errorKey;
    if (errorKey !== ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING && errorKey !== ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING) {
        return undefined;
    }
    return { translationKey: `error.${errorKey}`, params: { date: datePipe.transform(error.error?.params?.date) } };
}

/**
 * Displays the single, self-explanatory alert for the server's "assessment is not possible yet" 403 and reports whether
 * it did. Callers use the return value to skip their own, less specific error handling.
 *
 * @param error the failed response of an endpoint that opens an assessment
 * @param alertService used to display the alert
 * @param datePipe used to localize the date the server sent
 * @returns true if the error was the "assessment is not possible yet" one and has been displayed
 */
export function alertIfAssessmentNotPossibleYet(error: HttpErrorResponse, alertService: AlertService, datePipe: ArtemisDatePipe): boolean {
    const alert = getAssessmentNotPossibleYetAlert(error, datePipe);
    if (!alert) {
        return false;
    }
    alertService.closeAll();
    alertService.error(alert.translationKey, alert.params);
    return true;
}
