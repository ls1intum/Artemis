import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';

test.describe('File upload exercise participation', () => {
    let course: Course;
    let exercise: FileUploadExercise;

    test.beforeEach(async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
    });

    test('Starts a file upload exercise in the UI', async ({ login, courseOverview, fileUploadExerciseEditor }) => {
        await login(studentOne, `/courses/${course.id}/exercises`);
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

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
