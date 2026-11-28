import { describe, expect, it } from 'vitest';
import { ProgrammingExerciseParticipationType as ParticipationType } from 'app/programming/shared/entities/programming-exercise-participation.model';
import { programmingExerciseResultWarning } from './programming-exercise-result-warning.util';

describe('programmingExerciseResultWarning', () => {
    it.each([undefined, Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY])('does not infer an unexpected score from an unavailable value %s', (score) => {
        expect(programmingExerciseResultWarning({ score }, ParticipationType.TEMPLATE)).toBeUndefined();
        expect(programmingExerciseResultWarning({ score }, ParticipationType.SOLUTION)).toBeUndefined();
    });

    it('keeps failed-test warnings even for a 100% solution score or an unavailable score', () => {
        expect(programmingExerciseResultWarning({ score: 100, successful: true, testCaseCount: 3, passedTestCaseCount: 2 }, ParticipationType.SOLUTION)).toContain(
            'solutionFailsTest',
        );
        expect(programmingExerciseResultWarning({ testCaseCount: 3, passedTestCaseCount: 2 }, ParticipationType.SOLUTION)).toContain('solutionFailsTest');
    });

    it('accepts failing tests with 0% for the template and accepts fully passing bonus solutions', () => {
        expect(programmingExerciseResultWarning({ score: 0, successful: false }, ParticipationType.TEMPLATE)).toBeUndefined();
        expect(programmingExerciseResultWarning({ score: 100, successful: true }, ParticipationType.SOLUTION)).toBeUndefined();
        expect(programmingExerciseResultWarning({ score: 110, successful: false, testCaseCount: 3, passedTestCaseCount: 3 }, ParticipationType.SOLUTION)).toBeUndefined();
    });

    it('warns for failing tests even when bonus points lift the score above 100%', () => {
        expect(programmingExerciseResultWarning({ score: 110, successful: true, testCaseCount: 3, passedTestCaseCount: 2 }, ParticipationType.SOLUTION)).toContain(
            'solutionFailsTest',
        );
    });

    it('does not infer failed tests from the score-derived successful flag', () => {
        expect(programmingExerciseResultWarning({ score: 100, successful: false, testCaseCount: 3, passedTestCaseCount: 3 }, ParticipationType.SOLUTION)).toBeUndefined();
    });

    it('does not warn for missing results or assignment participations', () => {
        expect(programmingExerciseResultWarning(undefined, ParticipationType.SOLUTION)).toBeUndefined();
        expect(programmingExerciseResultWarning({ score: 80, successful: false }, ParticipationType.ASSIGNMENT)).toBeUndefined();
    });
});
