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
 * that drags the `.modeling-editor` container's height, clamped to [MIN_HEIGHT, MAX_HEIGHT].
 */
test.describe('Resizable modeling editor', { tag: '@fast' }, () => {
    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    /** Drags the bottom resize handle by the given vertical delta (negative = up), re-reading its box each call. */
    async function dragBottomHandle(page: Page, handle: Locator, deltaY: number): Promise<void> {
        const box = (await handle.boundingBox())!;
        expect(box).not.toBeNull();
        const startX = box.x + box.width / 2;
        const startY = box.y + box.height / 2;
        await page.mouse.move(startX, startY);
        await page.mouse.down();
        // Move in steps so the directive receives intermediate pointermove events.
        await page.mouse.move(startX, startY + deltaY, { steps: 12 });
        await page.mouse.up();
    }

    test('shows the bottom handle and resizes the editor height by dragging it', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        const container = page.locator('.modeling-editor').first();
        const handle = container.locator('.draggable-bottom');

        // The directive-driven handle and resized container both render in participation mode.
        await expect(handle).toBeVisible();
        await expect(container).toBeVisible();
        // Only the vertical (bottom) handle is configured here — no horizontal handle.
        await expect(container.locator('.draggable-right')).toHaveCount(0);

        const before = (await container.boundingBox())!;
        expect(before).not.toBeNull();

        // Drag the handle DOWN by 250px -> the container grows by roughly the same amount.
        await dragBottomHandle(page, handle, 250);
        const afterGrow = (await container.boundingBox())!;
        expect(afterGrow.height).toBeGreaterThan(before.height + 150);
        // The directive writes the clamped height to the host's inline style.
        await expect(container).toHaveAttribute('style', /height:\s*\d+px/);

        // Drag the handle UP by 150px -> the container shrinks again but stays at/above the minimum.
        await dragBottomHandle(page, handle, -150);
        const afterShrink = (await container.boundingBox())!;
        expect(afterShrink.height).toBeLessThan(afterGrow.height - 80);
        expect(afterShrink.height).toBeGreaterThanOrEqual(MODELING_EDITOR_MIN_HEIGHT - 5);
    });

    test('clamps the editor height to its minimum when dragging far up', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        const container = page.locator('.modeling-editor').first();
        const handle = container.locator('.draggable-bottom');
        await expect(handle).toBeVisible();

        // First grow so there is room to shrink, then drag far past the minimum.
        await dragBottomHandle(page, handle, 300);
        const grown = (await container.boundingBox())!;
        expect(grown.height).toBeGreaterThan(MODELING_EDITOR_MIN_HEIGHT);

        await dragBottomHandle(page, handle, -3000);
        const clamped = (await container.boundingBox())!;
        // Clamped to the configured minimum height (within a small rendering tolerance), not collapsed to 0.
        expect(clamped.height).toBeGreaterThanOrEqual(MODELING_EDITOR_MIN_HEIGHT - 5);
        expect(clamped.height).toBeLessThanOrEqual(MODELING_EDITOR_MIN_HEIGHT + 40);
        expect(clamped.height).toBeLessThan(grown.height);
    });

    test('does not resize when the handle is pressed without dragging', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        const container = page.locator('.modeling-editor').first();
        const handle = container.locator('.draggable-bottom');
        await expect(handle).toBeVisible();

        const before = (await container.boundingBox())!;
        // A press + release without movement (delta 0) must not change the height (no accidental resize on click).
        await dragBottomHandle(page, handle, 0);
        const after = (await container.boundingBox())!;
        expect(Math.abs(after.height - before.height)).toBeLessThanOrEqual(2);
    });
});
