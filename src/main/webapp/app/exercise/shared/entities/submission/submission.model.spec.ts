import { describe, expect, it } from 'vitest';
import {
    Submission,
    getCorrectionRoundOfResult,
    getSubmissionResultByCorrectionRound,
    setSubmissionResultByCorrectionRound,
} from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

/**
 * A correction round is an index into the submission's results, but Athena results are not correction rounds and are
 * skipped. Reading and writing therefore have to agree on that index space, otherwise saving an assessment lands in the
 * wrong slot and silently rewrites another round's result.
 */
describe('Submission model correction round accessors', () => {
    const resultWith = (id: number, assessmentType: AssessmentType): Result => ({ id, assessmentType }) as Result;

    /**
     * @param results the results in the order the server returned them
     * @returns a submission carrying those results
     */
    function submissionWith(results: Result[]): Submission {
        return { id: 1, results } as Submission;
    }

    it('should read the correction round result skipping an Athena result', () => {
        const athena = resultWith(10, AssessmentType.AUTOMATIC_ATHENA);
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const secondRound = resultWith(12, AssessmentType.MANUAL);
        const submission = submissionWith([athena, firstRound, secondRound]);

        expect(getSubmissionResultByCorrectionRound(submission, 0)).toBe(firstRound);
        expect(getSubmissionResultByCorrectionRound(submission, 1)).toBe(secondRound);
    });

    it('should write the correction round result into the slot it is read from when an Athena result is present', () => {
        const athena = resultWith(10, AssessmentType.AUTOMATIC_ATHENA);
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const secondRound = resultWith(12, AssessmentType.MANUAL);
        const submission = submissionWith([athena, firstRound, secondRound]);
        const savedSecondRound = resultWith(12, AssessmentType.MANUAL);

        setSubmissionResultByCorrectionRound(submission, savedSecondRound, 1);

        // The first correction round must be untouched: overwriting it is what made a saved second-round assessment
        // reappear as the first round's result.
        expect(getSubmissionResultByCorrectionRound(submission, 0)).toBe(firstRound);
        expect(getSubmissionResultByCorrectionRound(submission, 1)).toBe(savedSecondRound);
        expect(submission.results).toContain(athena);
    });

    it('should report the correction round of a result skipping an Athena result', () => {
        const athena = resultWith(10, AssessmentType.AUTOMATIC_ATHENA);
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const secondRound = resultWith(12, AssessmentType.MANUAL);
        const submission = submissionWith([athena, firstRound, secondRound]);

        // The raw positions are 1 and 2; as correction rounds they are 0 and 1.
        expect(getCorrectionRoundOfResult(submission, 11)).toBe(0);
        expect(getCorrectionRoundOfResult(submission, 12)).toBe(1);
    });

    it('should report no correction round for an unknown or Athena result', () => {
        const athena = resultWith(10, AssessmentType.AUTOMATIC_ATHENA);
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const submission = submissionWith([athena, firstRound]);

        expect(getCorrectionRoundOfResult(submission, 10)).toBeUndefined();
        expect(getCorrectionRoundOfResult(submission, 999)).toBeUndefined();
        expect(getCorrectionRoundOfResult(undefined, 11)).toBeUndefined();
    });

    it('should stay symmetric without Athena results', () => {
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const secondRound = resultWith(12, AssessmentType.MANUAL);
        const submission = submissionWith([firstRound, secondRound]);
        const savedSecondRound = resultWith(12, AssessmentType.MANUAL);

        setSubmissionResultByCorrectionRound(submission, savedSecondRound, 1);

        expect(getSubmissionResultByCorrectionRound(submission, 0)).toBe(firstRound);
        expect(getSubmissionResultByCorrectionRound(submission, 1)).toBe(savedSecondRound);
        expect(submission.latestResult).toBe(savedSecondRound);
    });

    it('should mark the newest correction round as the latest result even behind an Athena result', () => {
        const athena = resultWith(10, AssessmentType.AUTOMATIC_ATHENA);
        const firstRound = resultWith(11, AssessmentType.MANUAL);
        const submission = submissionWith([athena, firstRound]);
        const savedFirstRound = resultWith(11, AssessmentType.MANUAL);

        setSubmissionResultByCorrectionRound(submission, savedFirstRound, 0);

        expect(submission.latestResult).toBe(savedFirstRound);
    });
});
