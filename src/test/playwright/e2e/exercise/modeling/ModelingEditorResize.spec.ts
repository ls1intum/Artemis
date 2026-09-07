import { expect } from '@playwright/test';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseParticipation.id } as any;

const SUB_PIXEL_TOLERANCE = 1;

test.describe('Responsive modeling editor tile', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 1440, height: 960 } });

    let modelingExercise: ModelingExercise;

    test.beforeEach('Create modeling exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('fills the available tile without clipping and keeps chrome independent', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        const tile = page.locator('.modeling-submission-editor-tile');
        const frame = page.locator('.modeling-editor__frame');
        const editor = page.locator('.apollon-editor');
        await expect(editor).toBeVisible();

        const [tileBox, frameBox, editorBox] = await Promise.all([tile.boundingBox(), frame.boundingBox(), editor.boundingBox()]);
        expect(tileBox).not.toBeNull();
        expect(frameBox).not.toBeNull();
        expect(editorBox).not.toBeNull();
        expect(Math.abs(frameBox!.x - tileBox!.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(frameBox!.width - tileBox!.width)).toBeLessThanOrEqual(1);
        expect(frameBox!.y + frameBox!.height).toBeLessThanOrEqual(960);
        expect(editorBox!.height).toBeGreaterThan(400);

        await expect(frame).toHaveCSS('border-top-style', 'solid');
        const cornerRadius = await frame.evaluate((element) => getComputedStyle(element).borderTopLeftRadius);
        await expect(tile).toHaveCSS('border-top-left-radius', cornerRadius);
        await expect(page.locator('.modeling-editor')).toHaveCSS('border-top-left-radius', cornerRadius);
        await expect(page.locator('.modeling-submission').getByTestId('resizeable-container-left').first()).toHaveCSS('border-top-left-radius', cornerRadius);

        const statusIsland = page.locator('[data-apollon-region="top-left"] .modeling-editor__status-island');
        const actionIsland = page.locator('[data-apollon-region="top-right"] .modeling-editor__actions');
        const palette = page.locator('[data-apollon-control="apollon:palette"]');
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const minimap = page.locator('[data-apollon-control="apollon:minimap"]');
        await expect(statusIsland).toContainText(/All changes saved|Unsaved changes|Saving/);
        await expect(actionIsland).not.toContainText(/All changes saved|Unsaved changes|Saving/);

        const explanationSurface = page.locator('.modeling-explanation-surface__surface');
        const explanationLabel = page.locator('.modeling-explanation-surface__label');
        const explanationResizer = page.locator('.modeling-explanation-surface__resizer');
        const textarea = explanationSurface.locator('textarea');
        await expect(textarea).toBeVisible();
        await expect(explanationLabel).toContainText('Explanation');
        await expect(explanationResizer).toHaveAttribute('role', 'separator');
        await expect(explanationResizer).toHaveAttribute('aria-orientation', 'horizontal');
        await page.getByRole('button', { name: 'Collapse panel' }).click();
        await expect.poll(async () => (await frame.boundingBox())?.width ?? 0).toBeGreaterThan(640);
        const [textareaBox, explanationSurfaceBox, paletteBox, zoomBox, minimapBox] = await Promise.all([
            textarea.boundingBox(),
            explanationSurface.boundingBox(),
            palette.boundingBox(),
            zoom.boundingBox(),
            minimap.boundingBox(),
        ]);
        expect(textareaBox).not.toBeNull();
        expect(explanationSurfaceBox).not.toBeNull();
        expect(paletteBox).not.toBeNull();
        expect(zoomBox).not.toBeNull();
        expect(minimapBox).not.toBeNull();
        const chromeGap = await page
            .locator('.apollon-overlay-corner')
            .first()
            .evaluate((region) => Number.parseFloat(getComputedStyle(region).columnGap));
        expect(textareaBox!.y).toBeGreaterThanOrEqual(frameBox!.y);
        expect(explanationSurfaceBox!.x).toBeGreaterThanOrEqual(Math.max(paletteBox!.x + paletteBox!.width, zoomBox!.x + zoomBox!.width) + chromeGap - 1);
        expect(explanationSurfaceBox!.x + explanationSurfaceBox!.width).toBeLessThanOrEqual(minimapBox!.x - chromeGap + 1);
        expect(textareaBox!.x).toBeGreaterThan(explanationSurfaceBox!.x);
        expect(textareaBox!.x + textareaBox!.width).toBeLessThan(explanationSurfaceBox!.x + explanationSurfaceBox!.width);
        expect(textareaBox!.y).toBeGreaterThan(explanationSurfaceBox!.y);
        expect(textareaBox!.y + textareaBox!.height).toBeLessThan(explanationSurfaceBox!.y + explanationSurfaceBox!.height);

        const initialSurfacePlacement = { x: explanationSurfaceBox!.x, width: explanationSurfaceBox!.width };
        const showMinimap = minimap.getByRole('button', { name: 'Show minimap' });
        await showMinimap.click();
        const hideMinimap = minimap.getByRole('button', { name: 'Hide minimap' });
        await expect(hideMinimap).toBeVisible();
        await expect
            .poll(async () => {
                const [surfaceBox, mapBox] = await Promise.all([explanationSurface.boundingBox(), minimap.boundingBox()]);
                return !!surfaceBox && !!mapBox && surfaceBox.x + surfaceBox.width <= mapBox.x - chromeGap + 1;
            })
            .toBe(true);
        const expandedMinimapBox = await minimap.boundingBox();
        expect(expandedMinimapBox).not.toBeNull();
        expect(expandedMinimapBox!.width).toBeGreaterThan(minimapBox!.width + 100);

        await hideMinimap.click();
        await expect(showMinimap).toBeVisible();
        await expect
            .poll(async () => {
                const surfaceBox = await explanationSurface.boundingBox();
                return surfaceBox ? { x: Math.round(surfaceBox.x), width: Math.round(surfaceBox.width) } : undefined;
            })
            .toEqual({ x: Math.round(initialSurfacePlacement.x), width: Math.round(initialSurfacePlacement.width) });

        await textarea.fill(Array.from({ length: 10 }, (_, index) => `Explanation line ${index + 1}`).join('\n'));
        await expect.poll(async () => (await textarea.boundingBox())?.height ?? 0).toBeGreaterThan(textareaBox!.height + 40);

        await textarea.fill(Array.from({ length: 40 }, (_, index) => `Explanation line ${index + 1}`).join('\n'));
        await expect.poll(() => textarea.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);

        const resizeHandleBox = await explanationResizer.boundingBox();
        const automaticSurfaceBox = await explanationSurface.boundingBox();
        expect(resizeHandleBox).not.toBeNull();
        expect(automaticSurfaceBox).not.toBeNull();
        await page.mouse.move(resizeHandleBox!.x + resizeHandleBox!.width / 2, resizeHandleBox!.y + resizeHandleBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(resizeHandleBox!.x + resizeHandleBox!.width / 2, resizeHandleBox!.y - 100, { steps: 8 });
        await page.mouse.up();
        await expect.poll(async () => (await explanationSurface.boundingBox())?.height ?? 0).toBeGreaterThan(automaticSurfaceBox!.height + 70);

        await page.setViewportSize({ width: 640, height: 900 });
        await expect(frame).toBeVisible();
        await expect(explanationSurface).toBeVisible();
        await expect(textarea).toBeVisible();
        await expect(palette).toBeVisible();
        const [compactFrameBox, compactSurfaceBox, compactTextareaBox, compactPaletteBox] = await Promise.all([
            frame.boundingBox(),
            explanationSurface.boundingBox(),
            textarea.boundingBox(),
            palette.boundingBox(),
        ]);
        expect(compactFrameBox).not.toBeNull();
        expect(compactSurfaceBox).not.toBeNull();
        expect(compactTextareaBox).not.toBeNull();
        expect(compactPaletteBox).not.toBeNull();
        expect(compactSurfaceBox!.x).toBeGreaterThanOrEqual(compactFrameBox!.x - 1);
        expect(compactSurfaceBox!.x + compactSurfaceBox!.width).toBeLessThanOrEqual(compactFrameBox!.x + compactFrameBox!.width + 1);
        expect(compactSurfaceBox!.x).toBeGreaterThanOrEqual(compactPaletteBox!.x + compactPaletteBox!.width - 1);
        expect(compactTextareaBox!.x).toBeGreaterThan(compactSurfaceBox!.x);
        expect(compactTextareaBox!.x + compactTextareaBox!.width).toBeLessThan(compactSurfaceBox!.x + compactSurfaceBox!.width);
        expect(compactTextareaBox!.y).toBeGreaterThan(compactSurfaceBox!.y);
        expect(compactTextareaBox!.y + compactTextareaBox!.height).toBeLessThan(compactSurfaceBox!.y + compactSurfaceBox!.height);
        await expect
            .poll(() =>
                explanationSurface.evaluate((surface) => {
                    const content = surface.querySelector<HTMLElement>('.modeling-explanation-surface__content');
                    return !!content && content.scrollWidth <= content.clientWidth + 1;
                }),
            )
            .toBe(true);
    });

    test('keeps the full action island visible when the problem statement is widened', async ({ login, page, courseOverview }) => {
        await login(studentOne, `/courses/${course.id}/exercises/${modelingExercise.id}`);
        await courseOverview.startExercise(modelingExercise.id!);

        const problemResizeHandle = page.locator('p-splitter.resizable-panels-splitter > [role="separator"]');
        await expect(problemResizeHandle).toBeVisible();
        const handleBox = await problemResizeHandle.boundingBox();
        expect(handleBox).not.toBeNull();

        await page.mouse.move(handleBox!.x + handleBox!.width / 2, handleBox!.y + handleBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(handleBox!.x - 260, handleBox!.y + handleBox!.height / 2, { steps: 12 });
        await page.mouse.up();

        await expect
            .poll(async () => {
                const [frameBox, actionsBox] = await Promise.all([page.locator('.modeling-editor__frame').boundingBox(), page.locator('.modeling-editor__actions').boundingBox()]);
                if (!frameBox || !actionsBox) {
                    return false;
                }
                return actionsBox.x >= frameBox.x && actionsBox.x + actionsBox.width <= frameBox.x + frameBox.width + SUB_PIXEL_TOLERANCE;
            })
            .toBe(true);
        await expect(page.getByTestId('modeling-editor-fullscreen')).toBeVisible();
        await expect(page.locator('.modeling-editor__status-island span')).toBeVisible();
    });
});
