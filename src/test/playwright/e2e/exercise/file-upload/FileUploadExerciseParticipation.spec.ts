import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

test.describe('File upload exercise participation', { tag: '@fast' }, () => {
    let exercise: FileUploadExercise;

    test.beforeEach(async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
    });

    test('Starts a file upload exercise in the UI', async ({ login, courseOverview, fileUploadExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id!);
        await courseOverview.openRunningExercise(exercise.id!);

        // Verify the initial state of the text editor
        await fileUploadExerciseEditor.shouldShowExerciseTitleInHeader(exercise.title!);
        await fileUploadExerciseEditor.shouldShowProblemStatement();

        // Make a submission
        await fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
        const fileUploadResponse = await fileUploadExerciseEditor.submit();
        const submission: FileUploadSubmission = await fileUploadResponse.json();
        expect(submission.submitted).toBe(true);
        expect(fileUploadResponse.status()).toBe(200);
    });

    // Seed courses are persistent — no cleanup needed
});
