import { describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import {
    ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING,
    ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING,
    alertIfAssessmentNotPossibleYet,
    getAssessmentNotPossibleYetAlert,
    getAssessmentNotPossibleYetReason,
} from 'app/assessment/shared/util/assessment-availability.util';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

describe('AssessmentAvailabilityUtil', () => {
    const datePipe = { transform: (date: unknown) => `formatted:${dayjs(date as string).toISOString()}` } as ArtemisDatePipe;

    describe('getAssessmentNotPossibleYetReason', () => {
        it('should return undefined when the exercise carries no assessment dates, e.g. for course exercises', () => {
            expect(getAssessmentNotPossibleYetReason({} as Exercise)).toBeUndefined();
            expect(getAssessmentNotPossibleYetReason(undefined)).toBeUndefined();
        });

        it('should return undefined once assessment is possible', () => {
            const exercise = {
                latestExamEndDate: dayjs().subtract(2, 'hours'),
                assessmentPossibleFrom: dayjs().subtract(1, 'hour'),
            } as Exercise;

            expect(getAssessmentNotPossibleYetReason(exercise)).toBeUndefined();
        });

        it('should report the running exam and name the exam end date, like the server does for the same state', () => {
            // a programming exercise whose tests run 15 minutes after the exam ends
            const latestExamEndDate = dayjs().add(1, 'hour');
            const assessmentPossibleFrom = latestExamEndDate.add(15, 'minutes');
            const exercise = { latestExamEndDate, assessmentPossibleFrom } as Exercise;

            expect(getAssessmentNotPossibleYetReason(exercise)).toEqual({
                translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`,
                // the message names the exam end, but assessment only opens once the tests have run
                date: latestExamEndDate,
                assessmentPossibleFrom,
            });
        });

        it('should report pending tests when the exam has ended but the tests still have to run', () => {
            const assessmentPossibleFrom = dayjs().add(15, 'minutes');
            const exercise = { latestExamEndDate: dayjs().subtract(1, 'minute'), assessmentPossibleFrom } as Exercise;

            expect(getAssessmentNotPossibleYetReason(exercise)).toEqual({
                translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING}`,
                date: assessmentPossibleFrom,
                assessmentPossibleFrom,
            });
        });
    });

    describe('getAssessmentNotPossibleYetAlert', () => {
        it.each([ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, ASSESSMENT_NOT_POSSIBLE_TESTS_PENDING])('should localize the date the server sent for %s', (errorKey) => {
            const date = '2026-07-31T12:35:00Z';
            const error = new HttpErrorResponse({ error: { errorKey, params: { date } } });

            expect(getAssessmentNotPossibleYetAlert(error, datePipe)).toEqual({
                translationKey: `error.${errorKey}`,
                params: { date: `formatted:${dayjs(date).toISOString()}` },
            });
        });

        it('should return undefined for unrelated errors', () => {
            const error = new HttpErrorResponse({ error: { errorKey: 'lockedSubmissionsLimitReached' } });

            expect(getAssessmentNotPossibleYetAlert(error, datePipe)).toBeUndefined();
            expect(getAssessmentNotPossibleYetAlert(new HttpErrorResponse({}), datePipe)).toBeUndefined();
        });
    });

    describe('alertIfAssessmentNotPossibleYet', () => {
        it('should display exactly one alert and report that it handled the error', () => {
            const alertService = { closeAll: vi.fn(), error: vi.fn() } as unknown as AlertService;
            const error = new HttpErrorResponse({ error: { errorKey: ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, params: { date: '2026-07-31T12:35:00Z' } } });

            expect(alertIfAssessmentNotPossibleYet(error, alertService, datePipe)).toBe(true);
            expect(alertService.closeAll).toHaveBeenCalledOnce();
            expect(alertService.error).toHaveBeenCalledExactlyOnceWith(`error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, {
                date: `formatted:${dayjs('2026-07-31T12:35:00Z').toISOString()}`,
            });
        });

        it('should not alert for unrelated errors so that the caller keeps its own handling', () => {
            const alertService = { closeAll: vi.fn(), error: vi.fn() } as unknown as AlertService;

            expect(alertIfAssessmentNotPossibleYet(new HttpErrorResponse({ error: { errorKey: 'other' } }), alertService, datePipe)).toBe(false);
            expect(alertService.error).not.toHaveBeenCalled();
        });
    });
});
