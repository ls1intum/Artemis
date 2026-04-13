import { TextExercise } from 'app/text/shared/entities/text-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Fixtures } from '../../../fixtures/fixtures';
import { expect } from '@playwright/test';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

test.describe('Text exercise participation', { tag: '@fast' }, () => {
    let exercise: TextExercise;

    test.beforeEach('Create text exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createTextExercise({ course });
    });

    test('Makes a text exercise submission as student', async ({ login, courseOverview, textExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id!);
        await courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        await textExerciseEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        await textExerciseEditor.shouldShowProblemStatement();

        // Make a submission
        const submission = await Fixtures.get('loremIpsum.txt');
        await textExerciseEditor.shouldShowNumberOfWords(0);
        await textExerciseEditor.shouldShowNumberOfCharacters(0);
        await textExerciseEditor.typeSubmission(exercise.id!, submission!);
        await textExerciseEditor.shouldShowNumberOfWords(74);
        await textExerciseEditor.shouldShowNumberOfCharacters(451);
        const response = await textExerciseEditor.submit();
        const textSubmission: TextSubmission = await response.json();
        expect(textSubmission.text).toBe(submission);
        expect(textSubmission.submitted).toBe(true);
        expect(response.status()).toBe(200);
    });

    // Seed courses are persistent — no cleanup needed
});
