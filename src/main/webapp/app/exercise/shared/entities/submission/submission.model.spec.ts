import { describe, expect, it } from 'vitest';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import {
    Submission,
    getLatestSubmissionResult,
    getNewestResult,
    getSubmissionResultByCorrectionRound,
    setLatestSubmissionResult,
} from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

function resultWith(id: number | undefined, correctionRound?: number, assessmentType?: AssessmentType): Result {
    const result = new Result();
    result.id = id;
    result.correctionRound = correctionRound;
    result.assessmentType = assessmentType;
    return result;
}

function submissionWith(results: (Result | undefined)[]): Submission {
    const submission = new ProgrammingSubmission();
    submission.results = results as Result[];
    return submission;
}

describe('Submission model', () => {
    describe('getNewestResult', () => {
        it('should return the result with the highest id, whatever the order', () => {
            const newest = resultWith(9);

            expect(getNewestResult([resultWith(3), newest, resultWith(7)])).toBe(newest);
            expect(getNewestResult([newest, resultWith(7), resultWith(3)])).toBe(newest);
        });

        it('should treat a result without an id as the newest one', () => {
            const unsaved = resultWith(undefined);

            expect(getNewestResult([resultWith(3), unsaved, resultWith(7)])).toBe(unsaved);
        });

        it('should ignore undefined entries and return undefined for nothing usable', () => {
            const only = resultWith(2);

            expect(getNewestResult([undefined, only, undefined])).toBe(only);
            expect(getNewestResult([undefined, undefined])).toBeUndefined();
            expect(getNewestResult([])).toBeUndefined();
            expect(getNewestResult(undefined)).toBeUndefined();
        });
    });

    describe('getLatestSubmissionResult', () => {
        it('should not depend on the position of the results', () => {
            const newest = resultWith(12);

            // The server holds the results in a set, so both orders are possible for the same data.
            expect(getLatestSubmissionResult(submissionWith([resultWith(4), newest]))).toBe(newest);
            expect(getLatestSubmissionResult(submissionWith([newest, resultWith(4)]))).toBe(newest);
        });
    });

    describe('setLatestSubmissionResult', () => {
        it('should keep the other results when the newest one is not the last element', () => {
            const newest = resultWith(25);
            const older = resultWith(24);
            const submission = submissionWith([newest, older]);

            setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));

            expect(submission.results).toEqual([newest, older]);
            expect(submission.latestResult).toBe(newest);
            expect(newest.submission).toBe(submission);
        });

        it('should replace the result that has the same id', () => {
            // The matching entry is not the last one, so overwriting the last entry would fail this test.
            const older = resultWith(24);
            const submission = submissionWith([resultWith(25), older]);
            const updated = resultWith(25);

            setLatestSubmissionResult(submission, updated);

            expect(submission.results).toHaveLength(2);
            expect(submission.results![0]).toBe(updated);
            expect(submission.results![1]).toBe(older);
            expect(submission.latestResult).toBe(updated);
        });

        it('should let a draft without an id replace the last entry', () => {
            // A cloned draft, as the example-submission editors send it, must not sit next to the result it was cloned from.
            const persisted = resultWith(24);
            const draft = resultWith(undefined);
            const submission = submissionWith([persisted, draft]);
            const clone = resultWith(undefined);

            setLatestSubmissionResult(submission, clone);

            expect(submission.results).toEqual([persisted, clone]);
            expect(submission.latestResult).toBe(clone);
        });

        it('should create the results list when there is none', () => {
            const submission = submissionWith(undefined as unknown as Result[]);
            const result = resultWith(1);

            setLatestSubmissionResult(submission, result);

            expect(submission.results).toEqual([result]);
            expect(submission.latestResult).toBe(result);
        });
    });

    describe('getSubmissionResultByCorrectionRound', () => {
        it('should match on the correction round rather than the position', () => {
            const second = resultWith(4, 1, AssessmentType.MANUAL);
            const first = resultWith(8, 0, AssessmentType.MANUAL);
            const submission = submissionWith([second, first]);

            expect(getSubmissionResultByCorrectionRound(submission, 0)).toBe(first);
            expect(getSubmissionResultByCorrectionRound(submission, 1)).toBe(second);
        });

        it('should return undefined for a round that has no result', () => {
            const submission = submissionWith([resultWith(8, 0, AssessmentType.MANUAL)]);

            expect(getSubmissionResultByCorrectionRound(submission, 1)).toBeUndefined();
        });

        it('should not return an Athena result for a correction round', () => {
            const submission = submissionWith([resultWith(8, 0, AssessmentType.AUTOMATIC_ATHENA)]);

            expect(getSubmissionResultByCorrectionRound(submission, 0)).toBeUndefined();
        });
    });
});
