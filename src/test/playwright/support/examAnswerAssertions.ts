import { expect } from '@playwright/test';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from './constants';
import { Fixtures } from '../fixtures/fixtures';

/**
 * Asserts, on a student exam summary as the server returns it, whether the text, quiz and modeling exercise hold the answer the tests
 * submit: the text fixture, the two ticked quiz options, and a model with the three components the tests drag in. `false` asserts that
 * the exercise holds no answer at all. Exercise types that are not listed in `expected` (programming, file upload) are not looked at.
 */
export async function expectStoredAnswers(summary: StudentExam, exercises: Exercise[], expected: Partial<Record<ExerciseType, boolean>>) {
    const submissionOf = (exerciseId: number) => summary.exercises!.find((candidate: Exercise) => candidate.id === exerciseId)?.studentParticipations?.[0]?.submissions?.[0] as any;

    for (const exercise of exercises) {
        const answered = expected[exercise.type as ExerciseType];
        if (answered === undefined) {
            continue;
        }
        const submission = submissionOf(exercise.id!);
        switch (exercise.type) {
            case ExerciseType.TEXT:
                expect(submission?.text ?? '', `text answer of '${exercise.exerciseGroup?.title}'`).toBe(answered ? await Fixtures.get(exercise.additionalData!.textFixture!) : '');
                break;
            case ExerciseType.QUIZ: {
                const selectedOptions = (submission?.submittedAnswers ?? []).flatMap((answer: any) => answer.selectedOptions ?? []);
                expect(selectedOptions, `quiz answer of '${exercise.exerciseGroup?.title}'`).toHaveLength(answered ? 2 : 0);
                break;
            }
            case ExerciseType.MODELING: {
                const nodeCount = submission?.model ? (JSON.parse(submission.model).nodes ?? []).length : 0;
                if (answered) {
                    expect(nodeCount, `model of '${exercise.exerciseGroup?.title}'`).toBeGreaterThanOrEqual(3);
                } else {
                    expect(nodeCount, `model of '${exercise.exerciseGroup?.title}'`).toBe(0);
                }
                break;
            }
        }
    }
}
