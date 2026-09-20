import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { SEED_COURSES } from '../../../support/seedData';
import { readResponseJson } from '../../../support/utils';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

test.describe('File upload exercise participation', { tag: '@fast' }, () => {
    let exercise: FileUploadExercise;

    test.beforeEach(async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
    });

    test('Starts a file upload exercise in the UI', async ({ login, page, courseOverview, fileUploadExerciseEditor, exerciseAPIRequests }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
        await courseOverview.startExercise(exercise.id!);

        // Verify the initial state of the file upload editor
        await courseOverview.shouldShowExerciseTitleInHeader(exercise.title!);
        await courseOverview.shouldShowProblemStatement();

        // Make a submission
        await fileUploadExerciseEditor.attachFile('pdf-test-file.pdf');
        const fileUploadResponse = await courseOverview.submitExercise('api/fileupload/exercises/*/file-upload-submissions');
        // The upload is a file-backed multipart POST, so Playwright cannot hold its response body in Node
        // and Chrome may drop it from its network buffer before it is read. Re-derive the same submission
        // with an idempotent GET rather than failing on a body that no longer exists.
        const submission: FileUploadSubmission = await readResponseJson(fileUploadResponse, async () => {
            const participationId = Number(page.url().split('/participate/')[1]?.split(/[/?#]/)[0]);
            expect(participationId, 'expected to be on the participation page after submitting').toBeGreaterThan(0);
            return await exerciseAPIRequests.getFileUploadSubmissionForParticipation(participationId);
        });
        expect(submission.submitted).toBe(true);
        expect(fileUploadResponse.status()).toBe(200);
    });

    // Seed courses are persistent — no cleanup needed
});
