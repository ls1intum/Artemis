import { expect } from '@playwright/test';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { expectNoScrollPastApollonCanvas } from '../../../support/utils';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

test.describe('Apollon canvas is never scrolled past', { tag: '@fast' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('the student participation view fits its frame in a short viewport', async ({ login, page, courseOverview }) => {
        await page.setViewportSize({ width: 1280, height: 720 });
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        await expectNoScrollPastApollonCanvas(page);
    });

    test('the exercise form scrolls, and gives the wheel back through scroll lock', async ({ login, page }) => {
        await login(admin, `/course-management/${course.id}/modeling-exercises/${modelingExercise.id}/edit`);
        await expect(page.locator('.apollon-editor')).toBeVisible();

        const pageScrolls = await page.evaluate(() => document.documentElement.scrollHeight > document.documentElement.clientHeight + 1);
        const containerScrolls = await page.locator('#course-body-container').evaluate((element) => element.scrollHeight > element.clientHeight + 1);
        expect(pageScrolls || containerScrolls).toBe(true);

        const scroller = page.locator('#course-body-container');
        const before = await scroller.evaluate((element) => element.scrollTop);
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const zoomBefore = (await zoom.textContent()) ?? '';

        await page.locator('.apollon-editor').hover();
        await page.mouse.wheel(0, 400);

        await expect.poll(() => scroller.evaluate((element) => element.scrollTop)).toBeGreaterThan(before);
        await expect(zoom).toHaveText(zoomBefore);
    });
});
