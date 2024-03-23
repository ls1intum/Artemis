import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Fixtures } from '../../../fixtures/fixtures';
import { expect } from '@playwright/test';
import { TextSubmission } from 'app/entities/text-submission.model';

test.describe('Text exercise participation', () => {
    let course: Course;
    let exercise: TextExercise;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exercise = await exerciseAPIRequests.createTextExercise({ course });
    });

    test('Makes a text exercise submission as student', async ({ login, courseOverview, textExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}/exercises`);
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

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
