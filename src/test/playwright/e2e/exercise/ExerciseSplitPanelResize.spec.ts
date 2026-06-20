import { Page, expect } from '@playwright/test';
import { admin, studentOne } from '../../support/users';
import { test } from '../../support/fixtures';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * End-to-end coverage for the split.js -> PrimeNG p-splitter migration. The student exercise details
 * view renders `jhi-resizable-panels` (a p-splitter) splitting the exercise content from the
 * problem-statement / discussion tabs. Dragging the gutter must repartition the two panels.
 */
test.describe('Resizable exercise split panel (p-splitter)', { tag: '@fast' }, () => {
    let textExercise: any;

    test.beforeEach('Create text exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        textExercise = await exerciseAPIRequests.createTextExercise({ course });
    });

    /** Drags the p-splitter gutter horizontally by the given delta, re-reading its box each call. */
    async function dragGutter(page: Page, gutter: any, deltaX: number): Promise<void> {
        const box = (await gutter.boundingBox())!;
        expect(box).not.toBeNull();
        const startX = box.x + box.width / 2;
        const startY = box.y + box.height / 2;
        await page.mouse.move(startX, startY);
        await page.mouse.down();
        await page.mouse.move(startX + deltaX, startY, { steps: 12 });
        await page.mouse.up();
    }

    test('repartitions the panels by dragging the splitter gutter', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
        await courseOverview.startExercise(textExercise.id!);

        const splitter = page.locator('jhi-resizable-panels p-splitter').first();
        const gutter = splitter.locator('.p-splitter-gutter').first();
        const leftPanel = splitter.locator('.p-splitterpanel').first();

        await expect(gutter).toBeVisible({ timeout: 30_000 });
        await expect(leftPanel).toBeVisible();

        const leftBefore = (await leftPanel.boundingBox())!;
        expect(leftBefore).not.toBeNull();

        // Drag the gutter to the right -> the left panel grows.
        await dragGutter(page, gutter, 180);
        const leftAfterGrow = (await leftPanel.boundingBox())!;
        expect(leftAfterGrow.width).toBeGreaterThan(leftBefore.width + 60);

        // Drag the gutter back to the left -> the left panel shrinks again.
        await dragGutter(page, gutter, -240);
        const leftAfterShrink = (await leftPanel.boundingBox())!;
        expect(leftAfterShrink.width).toBeLessThan(leftAfterGrow.width - 60);
    });
});
