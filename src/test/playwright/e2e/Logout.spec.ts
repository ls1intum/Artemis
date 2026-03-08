import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { admin, studentOne, studentTwo } from '../support/users';
import { SEED_COURSES } from '../support/seedData';

const course = { id: SEED_COURSES.general.id } as any;

test.describe('Logout tests', () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create a modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('Logs out after confirmation of dialog for unsaved changes on exercise mode', async ({ page, login, courseOverview, modelingExerciseEditor, navigationBar }) => {
        await login(studentOne);

        const exerciseID = modelingExercise.id!;
        await page.goto(`/courses/${course.id}/exercises/${exerciseID}`);
        await courseOverview.startExercise(exerciseID);
        await courseOverview.openRunningExercise(exerciseID);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 1);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 2);

        page.on('dialog', async (dialog) => {
            expect(dialog.message()).toContain('You have unsaved changes');
            await dialog.accept();
        });

        await navigationBar.logout();

        await page.waitForURL(process.env.BASE_URL!);
    });

    test('Stays logged in after dismissal of dialog for unsaved changes on exercise mode', async ({ login, page, courseOverview, modelingExerciseEditor, navigationBar }) => {
        await login(studentTwo);

        const exerciseID = modelingExercise.id!;
        await page.goto(`/courses/${course.id}/exercises/${exerciseID}`);
        await courseOverview.startExercise(exerciseID);
        await courseOverview.openRunningExercise(exerciseID);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 1);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 2);

        page.on('dialog', async (dialog) => {
            expect(dialog.message()).toContain('You have unsaved changes');
            await dialog.dismiss();
        });

        await navigationBar.logout();

        await page.waitForLoadState('load');
        expect(page.url()).not.toEqual(process.env.BASE_URL!);
    });

    test.afterEach('Delete exercise to prevent DB accumulation', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        await exerciseAPIRequests.deleteModelingExercise(modelingExercise.id!);
    });
});
