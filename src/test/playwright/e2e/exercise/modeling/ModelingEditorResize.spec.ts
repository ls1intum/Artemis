import { Locator, Page, expect } from '@playwright/test';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { MODELING_EDITOR_MIN_HEIGHT } from 'app/foundation/constants/modeling.constants';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

/**
 * End-to-end coverage for the in-house `jhiResizable` directive (the interact.js replacement).
 * The student modeling editor exposes a bottom resize handle (`resizeOptions = { verticalResize: true }`)
 * that drags the `.modeling-editor` container's height, clamped to [MIN_HEIGHT, MAX_HEIGHT]. We assert on
 * the inline height the directive writes (deterministic), not on the bounding box (which shifts while the
 * Apollon canvas loads). A tall viewport keeps the bottom handle on-screen for `page.mouse`.
 */
test.describe('Resizable modeling editor', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 1600, height: 1400 } });

    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    /** The inline height (px) the directive writes to the host, or 0 when none is set yet. */
    async function inlineHeight(container: Locator): Promise<number> {
        return container.evaluate((el: HTMLElement) => parseFloat(el.style.height) || 0);
    }

    /** Drags the bottom resize handle by the given vertical delta (negative = up). `hover()` hit-tests the handle. */
    async function dragBottomHandle(page: Page, container: Locator, deltaY: number): Promise<void> {
        const handle = container.locator('.draggable-bottom');
        await handle.scrollIntoViewIfNeeded();
        await handle.hover(); // actionability check: scrolls to + positions the mouse on the real handle (not an overlay).
        const box = (await handle.boundingBox())!;
        expect(box).not.toBeNull();
        const startX = box.x + box.width / 2;
        const startY = box.y + box.height / 2;
        await page.mouse.down();
        await page.mouse.move(startX, startY + deltaY, { steps: 12 });
        await page.mouse.up();
    }

    test('shows the bottom handle and resizes the editor height by dragging it', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        // The OUTER `.modeling-editor` is a plain wrapper; the INNER one (`#resizeContainer`) carries the
        // `jhiResizable` directive and gains the `resizable` class, so target it via `.modeling-editor.resizable`.
        const container = page.locator('.modeling-editor.resizable');
        const handle = container.locator('.draggable-bottom');

        // The directive-driven handle and resized container both render in participation mode.
        await expect(handle).toBeVisible();
        await expect(container).toBeVisible();
        // Only the vertical (bottom) handle is configured here — no horizontal handle.
        await expect(container.locator('.draggable-right')).toHaveCount(0);
        // Nothing has written an inline height yet.
        expect(await inlineHeight(container)).toBe(0);

        // Drag the handle DOWN -> the directive writes a larger inline height (>= the configured minimum).
        await dragBottomHandle(page, container, 220);
        await expect(container).toHaveAttribute('style', /height:\s*\d+(\.\d+)?px/);
        const grown = await inlineHeight(container);
        expect(grown).toBeGreaterThanOrEqual(MODELING_EDITOR_MIN_HEIGHT);

        // Drag the handle UP -> the height shrinks again but stays at/above the minimum.
        await dragBottomHandle(page, container, -140);
        const shrunk = await inlineHeight(container);
        expect(shrunk).toBeLessThan(grown);
        expect(shrunk).toBeGreaterThanOrEqual(MODELING_EDITOR_MIN_HEIGHT - 1);
    });

    test('clamps the editor height to its minimum when dragging far up', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        // The OUTER `.modeling-editor` is a plain wrapper; the INNER one (`#resizeContainer`) carries the
        // `jhiResizable` directive and gains the `resizable` class, so target it via `.modeling-editor.resizable`.
        const container = page.locator('.modeling-editor.resizable');
        await expect(container.locator('.draggable-bottom')).toBeVisible();

        // First grow so there is room to shrink, then drag far past the minimum.
        await dragBottomHandle(page, container, 300);
        const grown = await inlineHeight(container);
        expect(grown).toBeGreaterThan(MODELING_EDITOR_MIN_HEIGHT);

        await dragBottomHandle(page, container, -3000);
        const clamped = await inlineHeight(container);
        // Clamped to the configured minimum (within a 1px tolerance), not collapsed below it.
        expect(clamped).toBeGreaterThanOrEqual(MODELING_EDITOR_MIN_HEIGHT - 1);
        expect(clamped).toBeLessThanOrEqual(MODELING_EDITOR_MIN_HEIGHT + 1);
        expect(clamped).toBeLessThan(grown);
    });

    test('does not resize when the handle is pressed without dragging', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        // The OUTER `.modeling-editor` is a plain wrapper; the INNER one (`#resizeContainer`) carries the
        // `jhiResizable` directive and gains the `resizable` class, so target it via `.modeling-editor.resizable`.
        const container = page.locator('.modeling-editor.resizable');
        await expect(container.locator('.draggable-bottom')).toBeVisible();

        // Establish a known height first, then press+release without movement.
        await dragBottomHandle(page, container, 200);
        const beforeClick = await inlineHeight(container);
        await dragBottomHandle(page, container, 0);
        const afterClick = await inlineHeight(container);
        expect(Math.abs(afterClick - beforeClick)).toBeLessThanOrEqual(1);
    });
});
