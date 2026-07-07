import { Page, expect } from '@playwright/test';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * End-to-end coverage for the interact.js -> jhiResizable migration in the code editor grid.
 * The file-browser sidebar (`.editor-sidebar-left`) is resized by dragging its right-edge handle
 * (`#draggableIconForFileBrowser`, class `.rg-sidebar-left`).
 */
test.describe('Resizable code editor sidebar', { tag: '@fast' }, () => {
    let programmingExercise: any;

    test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        programmingExercise = await exerciseAPIRequests.createProgrammingExercise({ course });
    });

    test('resizes the file-browser sidebar by dragging its handle', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${programmingExercise.id}`);
        await courseOverview.startExercise(programmingExercise.id!);

        const sidebar = page.locator('.editor-sidebar-left').first();
        const handle = page.locator('#draggableIconForFileBrowser');

        // The editor (and its file-browser handle) appear once the participation + repository are ready.
        await expect(handle).toBeVisible({ timeout: 120_000 });
        await expect(sidebar).toBeVisible();

        const before = (await sidebar.boundingBox())!;
        expect(before).not.toBeNull();

        // Drag the right-edge handle to the right -> the file browser widens.
        const dragHandle = async (page: Page, deltaX: number) => {
            const box = (await handle.boundingBox())!;
            await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
            await page.mouse.down();
            await page.mouse.move(box.x + box.width / 2 + deltaX, box.y + box.height / 2, { steps: 12 });
            await page.mouse.up();
        };

        await dragHandle(page, 160);
        const afterGrow = (await sidebar.boundingBox())!;
        expect(afterGrow.width).toBeGreaterThan(before.width + 60);
        // The directive writes the clamped width to the sidebar's inline style.
        await expect(sidebar).toHaveAttribute('style', /width:\s*\d+px/);

        // Drag back to the left -> the file browser narrows again.
        await dragHandle(page, -120);
        const afterShrink = (await sidebar.boundingBox())!;
        expect(afterShrink.width).toBeLessThan(afterGrow.width - 40);
    });
});
