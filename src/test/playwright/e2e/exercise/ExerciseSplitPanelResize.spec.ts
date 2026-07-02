import { Locator, Page, expect } from '@playwright/test';
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

    /** Drags the p-splitter gutter horizontally by the given delta. `hover()` ensures the gutter is interactable. */
    async function dragGutter(page: Page, gutter: Locator, deltaX: number): Promise<void> {
        await gutter.hover();
        const box = (await gutter.boundingBox())!;
        expect(box).not.toBeNull();
        const startX = box.x + box.width / 2;
        const startY = box.y + box.height / 2;
        await page.mouse.down();
        await page.mouse.move(startX + deltaX, startY, { steps: 12 });
        await page.mouse.up();
    }

    /** Polls the panel width until two consecutive reads agree, so the splitter has finished its initial layout. */
    async function waitForSettledWidth(panel: Locator): Promise<number> {
        let previous = -1;
        for (let i = 0; i < 20; i++) {
            const width = (await panel.boundingBox())!.width;
            if (Math.abs(width - previous) < 1 && width > 0) {
                return width;
            }
            previous = width;
            await panel.page().waitForTimeout(100);
        }
        return previous;
    }

    test('repartitions the panels by dragging the splitter gutter', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
        await courseOverview.startExercise(textExercise.id!);

        const splitter = page.locator('jhi-resizable-panels p-splitter').first();
        const gutter = splitter.locator('.p-splitter-gutter').first();
        // PrimeNG 22 renamed the splitter panel class from `p-splitterpanel` to `p-splitter-panel`.
        const leftPanel = splitter.locator('.p-splitter-panel').first();

        await expect(gutter).toBeVisible({ timeout: 30_000 });
        await expect(leftPanel).toBeVisible();

        // The splitter re-lays-out once the projected right-panel content populates; wait for that to settle.
        const leftBefore = await waitForSettledWidth(leftPanel);
        expect(leftBefore).toBeGreaterThan(0);

        // Drag the gutter to the right -> the left panel grows. Poll, because the very first pointerdown after
        // load can land before the splitter's drag handler is wired; a second nudge then takes effect.
        await expect(async () => {
            await dragGutter(page, gutter, 180);
            const width = (await leftPanel.boundingBox())!.width;
            expect(width).toBeGreaterThan(leftBefore + 60);
        }).toPass({ timeout: 10_000 });
        const leftAfterGrow = (await leftPanel.boundingBox())!.width;

        // Drag the gutter back to the left -> the left panel shrinks again.
        await dragGutter(page, gutter, -240);
        const leftAfterShrink = (await leftPanel.boundingBox())!.width;
        expect(leftAfterShrink).toBeLessThan(leftAfterGrow - 60);
    });
});
