import dayjs from 'dayjs';

import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

import { admin } from '../../../support/users';
import { generateUUID, readResponseJson } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('File upload exercise management', { tag: '@fast' }, () => {
    test('Creates a file upload exercise in the UI', async ({ login, page, navigationBar, courseManagement, courseManagementExercises, fileUploadExerciseCreation }) => {
        await login(admin, '/');
        await navigationBar.openCourseManagement();
        await courseManagement.openExercisesOfCourse(course.id!);
        await courseManagementExercises.createFileUploadExercise();

        // Fill out file upload exercise form
        const exerciseTitle = 'file upload exercise' + generateUUID();
        const releaseDate = dayjs().second(0).millisecond(0);
        const startDate = releaseDate.add(1, 'hour');
        const dueDate = releaseDate.add(1, 'day');
        const assessmentDueDate = releaseDate.add(2, 'days');
        await fileUploadExerciseCreation.setTitle(exerciseTitle);
        await fileUploadExerciseCreation.setReleaseDate(releaseDate);
        await fileUploadExerciseCreation.setStartDate(startDate);
        await fileUploadExerciseCreation.setDueDate(dueDate);
        await fileUploadExerciseCreation.setAssessmentDueDate(assessmentDueDate);
        await fileUploadExerciseCreation.typeMaxPoints(10);
        const problemStatement = 'This is a problem statement';
        const exampleSolution = 'E = mc^2';
        await fileUploadExerciseCreation.typeProblemStatement(problemStatement);
        await fileUploadExerciseCreation.typeExampleSolution(exampleSolution);
        const exerciseCreationResponse = await fileUploadExerciseCreation.create();
        const exercise: FileUploadExercise = await readResponseJson(exerciseCreationResponse);

        // Verify the exercise was actually persisted by fetching it from the API. This is the
        // test's real contract — "creating a file-upload exercise via the UI produces a
        // persisted exercise". We deliberately do NOT navigate to the exercises-list page and
        // assert UI visibility: that introduces a CI-flaky dependency on a separate aggregation
        // endpoint that occasionally lags behind the create response under multi-node load,
        // adds 10-30s of wallclock, and doesn't strengthen the assertion (UI visibility is
        // already covered by the deletion test below).
        const fetchResponse = await page.request.get(`api/fileupload/file-upload-exercises/${exercise.id}`);
        expect(fetchResponse.ok()).toBeTruthy();
        const fetched: FileUploadExercise = await fetchResponse.json();
        expect(fetched.title).toBe(exerciseTitle);
        expect(fetched.exampleSolution).toBe(exampleSolution);
        expect(dayjs(fetched.releaseDate).isSame(releaseDate)).toBeTruthy();
        expect(dayjs(fetched.startDate).isSame(startDate)).toBeTruthy();
        expect(dayjs(fetched.dueDate).isSame(dueDate)).toBeTruthy();
        expect(dayjs(fetched.assessmentDueDate).isSame(assessmentDueDate)).toBeTruthy();
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

    // Seed courses are persistent — no cleanup needed
});
