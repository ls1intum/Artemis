import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('File upload exercise management', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test('Creates a file upload exercise in the UI', async ({ page, navigationBar, courseManagement, courseManagementExercises, fileUploadExerciseCreation }) => {
        await page.goto('/');
        await navigationBar.openCourseManagement();
        await courseManagement.openExercisesOfCourse(course.id!);
        await courseManagementExercises.createFileUploadExercise();

        // Fill out file upload exercise form
        const exerciseTitle = 'file upload exercise' + generateUUID();
        await fileUploadExerciseCreation.typeTitle(exerciseTitle);
        await fileUploadExerciseCreation.setReleaseDate(dayjs());
        await fileUploadExerciseCreation.setDueDate(dayjs().add(1, 'days'));
        await fileUploadExerciseCreation.setAssessmentDueDate(dayjs().add(2, 'days'));
        await fileUploadExerciseCreation.typeMaxPoints(10);
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        await fileUploadExerciseCreation.typeProblemStatement(problemStatement);
        await fileUploadExerciseCreation.typeExampleSolution(exampleSolution);
        const exerciseCreationResponse = await fileUploadExerciseCreation.create();
        const exercise: FileUploadExercise = await exerciseCreationResponse.json();

        // Make sure file upload exercise is shown in exercises list
        await page.goto(`course-management/${course.id}/exercises`);
        await expect(courseManagementExercises.getExercise(exercise.id!)).toBeVisible();
    });

    test.describe('File upload exercise deletion', () => {
        let exercise: FileUploadExercise;

        test.beforeEach('Create exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createFileUploadExercise({ course });
        });

        test('Deletes an existing file upload exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteFileUploadExercise(exercise);
            await expect(courseManagementExercises.getExercise(exercise.id!)).not.toBeAttached();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
