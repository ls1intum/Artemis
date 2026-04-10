import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { admin, studentOne, studentTwo } from '../support/users';
import { SEED_COURSES } from '../support/seedData';

const course = { id: SEED_COURSES.general.id } as any;

test.describe('Logout tests', () => {
    let textExercise: TextExercise;

    test.beforeEach('Create a text exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        textExercise = await exerciseAPIRequests.createTextExercise({ course });
    });

    test('Logs out after confirmation of dialog for unsaved changes on exercise mode', async ({ page, login, courseOverview, textExerciseEditor, navigationBar }) => {
        await login(studentOne);

        const exerciseID = textExercise.id!;
        await page.goto(`/courses/${course.id}/exercises/${exerciseID}`);
        await courseOverview.startExercise(exerciseID);
        await textExerciseEditor.typeSubmission(exerciseID, 'Some unsaved text content');

        page.on('dialog', async (dialog) => {
            await dialog.accept();
        });

        await navigationBar.logout();

        await page.waitForURL(`${process.env.BASE_URL!}/**`);
    });

    test('Stays logged in after dismissal of dialog for unsaved changes on exercise mode', async ({ login, page, courseOverview, textExerciseEditor, navigationBar }) => {
        await login(studentTwo);

        const exerciseID = textExercise.id!;
        await page.goto(`/courses/${course.id}/exercises/${exerciseID}`);
        await courseOverview.startExercise(exerciseID);
        await textExerciseEditor.typeSubmission(exerciseID, 'Some unsaved text content');

        page.on('dialog', async (dialog) => {
            await dialog.dismiss();
        });

        await navigationBar.logout();

        await page.waitForLoadState('load');
        const currentUrl = page.url();
        expect(currentUrl).toContain(`/courses/${course.id}/exercises`);
    });

    test.afterEach('Delete exercise to prevent DB accumulation', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        await exerciseAPIRequests.deleteTextExercise(textExercise.id!);
    });
});
