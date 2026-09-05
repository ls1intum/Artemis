import { describe, expect, it } from 'vitest';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission, getLatestSubmissionResult, getNewestResult, getSubmissionResultByCorrectionRound } from 'app/exercise/shared/entities/submission/submission.model';
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
