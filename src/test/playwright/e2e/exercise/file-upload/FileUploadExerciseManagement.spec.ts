import dayjs from 'dayjs';

import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('File upload exercise management', { tag: '@fast' }, () => {
    test('Creates a file upload exercise in the UI', async ({ login, page, navigationBar, courseManagement, courseManagementExercises, fileUploadExerciseCreation }) => {
        // Login → openCourseManagement → openExercises → createFileUploadExercise + form
        // interactions + final exercises-list page render exceeds 60s @fast under multi-node
        // CI load. Bump to 180s via test.slow().
        test.slow();
        await login(admin, '/');
        await navigationBar.openCourseManagement();
        await courseManagement.openExercisesOfCourse(course.id!);
        await courseManagementExercises.createFileUploadExercise();

        // Fill out file upload exercise form
        const exerciseTitle = 'file upload exercise' + generateUUID();
        await fileUploadExerciseCreation.setTitle(exerciseTitle);
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

        // Make sure file upload exercise is shown in exercises list. The exercises-list
        // endpoint occasionally lags behind the create response under multi-node CI load,
        // so the freshly created card may not appear in the first render. Reload until
        // visible (same pattern used by the text-exercise creation test).
        const card = courseManagementExercises.getExercise(exercise.id!);
        const visibleWithin = async (timeout: number): Promise<boolean> =>
            card
                .waitFor({ state: 'visible', timeout })
                .then(() => true)
                .catch(() => false);
        await page.goto(`/course-management/${course.id}/exercises`);
        await page.waitForLoadState('load');
        for (let attempt = 0; attempt < 3; attempt++) {
            if (await visibleWithin(20_000)) {
                break;
            }
            if (attempt === 2) {
                throw new Error(`Newly created file upload exercise card #exercise-card-${exercise.id!} did not appear after 3 reloads`);
            }
            await page.reload();
            await page.waitForLoadState('load');
        }
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
