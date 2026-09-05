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

    /**
     * Polls the panel width until two consecutive reads agree, so the splitter has finished its initial layout.
     * <p>
     * Only a width that repeated is returned. A panel that is still being laid out reports a different width on every
     * poll, and returning the last of those as "settled" hands an in-flight layout to the resize assertion, which then
     * compares against a number that was never the panel's resting width.
     */
    async function waitForSettledWidth(panel: Locator): Promise<number> {
        let previous: number | undefined;
        for (let i = 0; i < 20; i++) {
            // No box means the panel is between renders, which is the opposite of settled: keep polling instead of
            // dereferencing null, which turned a panel that was still laying itself out into a TypeError.
            const box = await panel.boundingBox();
            if (box && box.width > 0) {
                if (previous !== undefined && Math.abs(box.width - previous) < 1) {
                    return box.width;
                }
                previous = box.width;
            } else {
                // The reads have to be consecutive: keeping the width from before a render gap would let it agree with
                // one from after the gap and pass a layout that was never stable across two polls as settled.
                previous = undefined;
            }
            await panel.page().waitForTimeout(100);
        }
        throw new Error(
            previous === undefined
                ? 'The panel never reported a measurable width, so there is no settled layout to compare a resize against.'
                : `The panel width never settled, last read ${previous}px, so there is no resting layout to compare a resize against.`,
        );
    }

    test('repartitions the panels by dragging the splitter gutter', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${textExercise.id}`);
        await courseOverview.startExercise(textExercise.id!);

        const splitter = page.locator('jhi-resizable-panels p-splitter').first();
        const gutter = splitter.getByTestId('splitter-gutter').first();
        const leftPanel = splitter.getByTestId('splitter-panel').first();

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

        // Drag the gutter back to the left -> the left panel shrinks again. Polled for the same reason as the grow
        // drag above: a pointerdown can land while the splitter is re-laying out, and the gesture is then lost. The
        // observed failure was exactly that - the panel moved about 20px instead of 240.
        await expect(async () => {
            await dragGutter(page, gutter, -240);
            const width = (await leftPanel.boundingBox())!.width;
            expect(width).toBeLessThan(leftAfterGrow - 60);
        }).toPass({ timeout: 10_000 });
    });
});
