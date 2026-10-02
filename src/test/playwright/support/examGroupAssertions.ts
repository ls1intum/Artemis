import { expect } from '@playwright/test';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from './constants';
import { ExamAPIRequests } from './requests/ExamAPIRequests';

/**
 * Asserts that the server holds exactly one exercise in the given exercise group and that it is the one that was just created:
 * its title, its type and its points. What the exercise groups page lists is only what the client shows; this is what was stored.
 */
export async function expectExerciseInGroup(
    examAPIRequests: ExamAPIRequests,
    exam: Exam,
    group: ExerciseGroup,
    expected: { title: string; type: ExerciseType; maxPoints: number },
) {
    const stored = (await examAPIRequests.getExerciseGroups(exam)).find((candidate) => candidate.id === group.id);
    expect(stored, `exercise group ${group.id} of exam ${exam.id}`).toBeDefined();
    expect(stored!.exercises, `the exercises of group ${group.id}`).toHaveLength(1);
    const [exercise] = stored!.exercises!;
    expect(exercise.title).toBe(expected.title);
    expect(exercise.type).toBe(expected.type);
    expect(exercise.maxPoints).toBe(expected.maxPoints);
    return exercise;
}
