import { expect } from '@playwright/test';

import { instructor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Fullscreen modeling editor', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 1440, height: 1000 } });

    test('keeps editor chrome and the example explanation responsive', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const actions = page.locator('.modeling-editor__actions');

        const diagramTypeIsland = page.locator('[data-apollon-region="top-left"] .modeling-editor__top-left');
        const diagramTypeSelect = diagramTypeIsland.getByRole('combobox', { name: 'Diagram Type' });
        await expect(diagramTypeSelect).toBeVisible();
        await expect(diagramTypeSelect).toContainText('Class Diagram');
        await diagramTypeSelect.scrollIntoViewIfNeeded();
        await diagramTypeSelect.click();
        await expect(diagramTypeSelect).toBeFocused();
        await page.getByRole('option', { name: 'Activity Diagram' }).click();
        await expect(diagramTypeSelect).toContainText('Activity Diagram');
        await expect(diagramTypeIsland).toBeVisible();
        await diagramTypeSelect.click();
        await page.getByRole('option', { name: 'Class Diagram' }).click();
        await expect(diagramTypeSelect).toContainText('Class Diagram');

        const diagramTypeBox = await diagramTypeIsland.boundingBox();
        const actionsBox = await actions.boundingBox();
        expect(diagramTypeBox).not.toBeNull();
        expect(actionsBox).not.toBeNull();
        expect(diagramTypeBox!.x).toBeLessThan(actionsBox!.x);
        expect(diagramTypeBox!.x + diagramTypeBox!.width).toBeLessThanOrEqual(actionsBox!.x);

        const helpButton = page.getByRole('button', { name: 'Help' });
        await expect(helpButton).toContainText('Help');

        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        await expect(fullscreenButton).toBeVisible();
        await expect(fullscreenButton).toContainText('Fullscreen');
        for (const button of [helpButton, fullscreenButton]) {
            await expect(button).toHaveAttribute('data-slot', 'button');
            await expect(button).toHaveAttribute('data-variant', 'ghost');
            await expect(button).toHaveAttribute('data-size', 'sm');
        }
        const [helpButtonBox, fullscreenButtonBox] = await Promise.all([helpButton.boundingBox(), fullscreenButton.boundingBox()]);
        expect(helpButtonBox).not.toBeNull();
        expect(fullscreenButtonBox).not.toBeNull();
        expect(helpButtonBox!.height).toBe(28);
        expect(fullscreenButtonBox!.height).toBe(helpButtonBox!.height);
        await expect(page.locator('.modeling-editor__problem-statement')).toBeHidden();

        const problemStatementEditor = page.locator('#field_problemStatement .monaco-editor');
        await problemStatementEditor.click();
        await page.keyboard.insertText('## Design task\n\nModel a library with books and authors.');
        await expect(page.locator('.modeling-editor__problem-statement-panel')).toContainText('Design task');

        const bottomCenter = page.locator('.modeling-exercise-example-explanation');
        const palette = page.locator('[data-apollon-control="apollon:palette"]');
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const minimap = page.locator('[data-apollon-control="apollon:minimap"]');
        const editorFrame = page.locator('.modeling-editor__frame');
        const chromeGap = await page
            .locator('.apollon-overlay-corner')
            .first()
            .evaluate((region) => Number.parseFloat(getComputedStyle(region).columnGap));
        await expect(bottomCenter).toBeVisible();
        await bottomCenter.scrollIntoViewIfNeeded();
        const frameBefore = await editorFrame.boundingBox();
        const zoomBefore = await zoom.boundingBox();
        const minimapBefore = await minimap.boundingBox();
        const viewportTransformBefore = await page.locator('.modeling-editor__frame .react-flow__viewport').evaluate((viewport) => getComputedStyle(viewport).transform);
        const exampleExplanationSurface = bottomCenter.locator('.modeling-explanation-surface__surface');
        const exampleExplanationEditor = page.locator('.modeling-markdown-explanation-editor__editor');
        const exampleExplanationNotch = bottomCenter.locator('.modeling-explanation-surface__notch');
        await expect(exampleExplanationSurface).toBeVisible();
        await expect(exampleExplanationNotch).toContainText('Example Solution Explanation');
        const exampleExplanationSurfaceBox = await exampleExplanationSurface.boundingBox();
        const exampleExplanationEditorBox = await exampleExplanationEditor.boundingBox();
        const explanationPaletteBox = await palette.boundingBox();
        const zoomBox = await zoom.boundingBox();
        const minimapBox = await minimap.boundingBox();
        expect(exampleExplanationSurfaceBox).not.toBeNull();
        expect(exampleExplanationEditorBox).not.toBeNull();
        expect(explanationPaletteBox).not.toBeNull();
        expect(zoomBox).not.toBeNull();
        expect(minimapBox).not.toBeNull();
        expect(exampleExplanationSurfaceBox!.height).toBeGreaterThanOrEqual(133);
        expect(exampleExplanationEditorBox!.x).toBeGreaterThan(exampleExplanationSurfaceBox!.x);
        expect(exampleExplanationEditorBox!.x + exampleExplanationEditorBox!.width).toBeLessThan(exampleExplanationSurfaceBox!.x + exampleExplanationSurfaceBox!.width);
        expect(exampleExplanationEditorBox!.y).toBeGreaterThan(exampleExplanationSurfaceBox!.y);
        expect(exampleExplanationEditorBox!.y + exampleExplanationEditorBox!.height).toBeLessThan(exampleExplanationSurfaceBox!.y + exampleExplanationSurfaceBox!.height);
        expect(exampleExplanationSurfaceBox!.x).toBeGreaterThanOrEqual(
            Math.max(explanationPaletteBox!.x + explanationPaletteBox!.width, zoomBox!.x + zoomBox!.width) + chromeGap - 1,
        );
        expect(exampleExplanationSurfaceBox!.x + exampleExplanationSurfaceBox!.width).toBeLessThanOrEqual(minimapBox!.x - chromeGap + 1);
        await expect(page.locator('[data-apollon-region="bottom-center"] .modeling-editor__bottom-center:not(.modeling-editor__bottom-center--elevated)')).toBeVisible();

        const showMinimap = minimap.getByRole('button', { name: 'Show minimap' });
        await showMinimap.click();
        const hideMinimap = minimap.getByRole('button', { name: 'Hide minimap' });
        await expect(hideMinimap).toBeVisible();
        await expect
            .poll(async () => {
                const [surfaceBox, mapBox] = await Promise.all([exampleExplanationSurface.boundingBox(), minimap.boundingBox()]);
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
                const surfaceBox = await exampleExplanationSurface.boundingBox();
                return surfaceBox ? { x: Math.round(surfaceBox.x), width: Math.round(surfaceBox.width) } : undefined;
            })
            .toEqual({ x: Math.round(exampleExplanationSurfaceBox!.x), width: Math.round(exampleExplanationSurfaceBox!.width) });

        const explanationLabel = exampleExplanationNotch.locator('span');
        expect(await explanationLabel.evaluate((label) => label.scrollWidth <= label.clientWidth + 1)).toBe(true);
        const markdownFullscreenButton = exampleExplanationEditor.locator('.md-action-palette .md-toolbar-btn').last();
        await markdownFullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement?.classList.contains('h-full'))).toBe(true);
        await markdownFullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect.poll(async () => (await exampleExplanationSurface.boundingBox())?.width ?? 0).toBeLessThanOrEqual(exampleExplanationSurfaceBox!.width + 1);

        const resizeHandleBox = await exampleExplanationNotch.boundingBox();
        expect(resizeHandleBox).not.toBeNull();
        const resizeHandleHitTarget = await exampleExplanationNotch.evaluate((notch) => {
            const rect = notch.getBoundingClientRect();
            const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
            return {
                isResizeHandle: hit === notch || notch.contains(hit) || hit?.classList.contains('modeling-explanation-surface__resizer'),
                tagName: hit?.tagName,
                className: hit?.getAttribute('class'),
            };
        });
        expect(resizeHandleHitTarget.isResizeHandle, JSON.stringify(resizeHandleHitTarget)).toBe(true);
        await page.mouse.move(resizeHandleBox!.x + resizeHandleBox!.width / 2, resizeHandleBox!.y + resizeHandleBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(resizeHandleBox!.x + resizeHandleBox!.width / 2, resizeHandleBox!.y - 55, { steps: 6 });
        await page.mouse.up();
        await expect(exampleExplanationSurface).toHaveClass(/modeling-explanation-surface__surface--manually-sized/);
        await expect.poll(async () => (await exampleExplanationSurface.boundingBox())?.height ?? 0).toBeGreaterThan(exampleExplanationSurfaceBox!.height + 40);

        await page.setViewportSize({ width: 900, height: 900 });
        await expect(bottomCenter).toBeVisible();
        await expect
            .poll(async () => {
                const [surfaceBox, paletteBox, zoomBox, mapBox] = await Promise.all([
                    exampleExplanationSurface.boundingBox(),
                    palette.boundingBox(),
                    zoom.boundingBox(),
                    minimap.boundingBox(),
                ]);
                if (!surfaceBox || !paletteBox || !zoomBox || !mapBox) {
                    return false;
                }
                const placementLeft = Math.max(paletteBox.x + paletteBox.width, zoomBox.x + zoomBox.width) + chromeGap - 1;
                return surfaceBox.x >= placementLeft && surfaceBox.x + surfaceBox.width <= mapBox.x - chromeGap + 1;
            })
            .toBe(true);

        await page.setViewportSize({ width: 640, height: 900 });
        await expect
            .poll(async () => {
                const [frameBox, surfaceBox, paletteBox] = await Promise.all([editorFrame.boundingBox(), exampleExplanationSurface.boundingBox(), palette.boundingBox()]);
                if (!frameBox || !surfaceBox || !paletteBox) {
                    return false;
                }
                return (
                    surfaceBox.x >= frameBox.x + chromeGap - 3 &&
                    surfaceBox.x + surfaceBox.width <= frameBox.x + frameBox.width - chromeGap + 3 &&
                    surfaceBox.x >= paletteBox.x + paletteBox.width + chromeGap - 1
                );
            })
            .toBe(true);
        const [compactFrameBox, compactSurfaceBox, compactEditorBox, compactPaletteBox] = await Promise.all([
            editorFrame.boundingBox(),
            exampleExplanationSurface.boundingBox(),
            page.locator('.modeling-markdown-explanation-editor__editor').boundingBox(),
            palette.boundingBox(),
        ]);
        expect(compactFrameBox).not.toBeNull();
        expect(compactSurfaceBox).not.toBeNull();
        expect(compactEditorBox).not.toBeNull();
        expect(compactPaletteBox).not.toBeNull();
        expect(compactSurfaceBox!.x).toBeGreaterThanOrEqual(compactFrameBox!.x + chromeGap - 3);
        expect(compactSurfaceBox!.x + compactSurfaceBox!.width).toBeLessThanOrEqual(compactFrameBox!.x + compactFrameBox!.width - chromeGap + 3);
        expect(compactSurfaceBox!.x).toBeGreaterThanOrEqual(compactPaletteBox!.x + compactPaletteBox!.width + chromeGap - 1);
        expect(compactEditorBox!.x).toBeGreaterThan(compactSurfaceBox!.x);
        expect(compactEditorBox!.x + compactEditorBox!.width).toBeLessThan(compactSurfaceBox!.x + compactSurfaceBox!.width);

        await page.setViewportSize({ width: 1440, height: 1000 });
        const frameAfter = await editorFrame.boundingBox();
        const zoomAfter = await zoom.boundingBox();
        const minimapAfter = await minimap.boundingBox();
        const viewportTransformAfter = await page.locator('.modeling-editor__frame .react-flow__viewport').evaluate((viewport) => getComputedStyle(viewport).transform);
        expect(frameBefore).not.toBeNull();
        expect(frameAfter).not.toBeNull();
        expect(zoomBefore).not.toBeNull();
        expect(zoomAfter).not.toBeNull();
        expect(minimapBefore).not.toBeNull();
        expect(minimapAfter).not.toBeNull();
        expect(viewportTransformAfter).toBe(viewportTransformBefore);
        const bottomInset = (frame: NonNullable<typeof frameBefore>, control: NonNullable<typeof zoomBefore>) => frame.y + frame.height - (control.y + control.height);
        expect(Math.abs(bottomInset(frameBefore!, zoomBefore!) - bottomInset(frameAfter!, zoomAfter!))).toBeLessThanOrEqual(1);
        expect(Math.abs(bottomInset(frameBefore!, minimapBefore!) - bottomInset(frameAfter!, minimapAfter!))).toBeLessThanOrEqual(1);
    });

    test('keeps the fullscreen problem statement stable while toggling and resizing it', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const actions = page.locator('.modeling-editor__actions');
        const diagramTypeIsland = page.locator('[data-apollon-region="top-left"] .modeling-editor__top-left');
        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        const problemStatementEditor = page.locator('#field_problemStatement .monaco-editor');
        await problemStatementEditor.click();
        await page.keyboard.insertText('## Design task\n\nModel a library with books and authors.');
        await expect(page.locator('.modeling-editor__problem-statement-panel')).toContainText('Design task');

        const bottomCenter = page.locator('.modeling-exercise-example-explanation');
        const chromeGap = await page
            .locator('.apollon-overlay-corner')
            .first()
            .evaluate((region) => Number.parseFloat(getComputedStyle(region).columnGap));

        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);

        const fullscreenFrame = page.locator('.modeling-editor__frame--fullscreen');
        await expect(fullscreenFrame).toBeVisible();
        await expect(fullscreenFrame.locator('[data-apollon-region="top-left"] #field_diagramType')).toBeVisible();
        await expect(fullscreenFrame.locator('.modeling-markdown-explanation-editor__editor')).toBeVisible();
        await expect(fullscreenFrame.locator('.scroll-overlay')).toHaveCount(0);

        const problemStatementIsland = fullscreenFrame.locator('[data-apollon-region="right-rail"] .modeling-editor__problem-statement');
        const problemStatementPanel = problemStatementIsland.locator('.modeling-editor__problem-statement-panel');
        const problemStatementButton = problemStatementIsland.getByTestId('modeling-editor-problem-statement');
        const problemStatementTriggerIsland = problemStatementIsland.locator('.modeling-editor__problem-statement-trigger-island');
        const horizontalProblemStatementResizer = problemStatementIsland.locator('.modeling-editor__problem-statement-resizer--left');
        const verticalProblemStatementResizer = problemStatementIsland.locator('.modeling-editor__problem-statement-resizer--bottom');
        await expect(problemStatementIsland).toBeVisible();
        await expect(problemStatementButton).toBeVisible();
        await expect(problemStatementButton).toHaveAttribute('aria-expanded', 'true');
        await expect(problemStatementPanel).toBeVisible();
        await expect(problemStatementPanel).toContainText('Design task');
        await expect(problemStatementPanel).toContainText('Model a library with books and authors.');
        await expect(horizontalProblemStatementResizer).toBeVisible();
        await expect(verticalProblemStatementResizer).toBeVisible();
        expect(
            await problemStatementButton.locator('span').evaluate((label) => {
                const style = getComputedStyle(label);
                return { transform: style.transform, writingMode: style.writingMode };
            }),
        ).toEqual({ transform: 'none', writingMode: 'horizontal-tb' });
        const disclosureBoxBefore = await problemStatementButton.boundingBox();
        const triggerIslandBoxBefore = await problemStatementTriggerIsland.boundingBox();
        const explanationBoxBeforeStatement = await bottomCenter.boundingBox();
        const fullscreenActionsBox = await actions.boundingBox();
        expect(fullscreenActionsBox).not.toBeNull();
        expect(triggerIslandBoxBefore).not.toBeNull();
        expect(triggerIslandBoxBefore!.y - (fullscreenActionsBox!.y + fullscreenActionsBox!.height)).toBeGreaterThanOrEqual(chromeGap - 1);
        await problemStatementButton.click();
        await expect(problemStatementPanel).toBeHidden();
        await expect(problemStatementButton).toHaveAttribute('aria-expanded', 'false');
        await problemStatementButton.click();
        await expect(problemStatementPanel).toBeVisible();
        await expect(problemStatementButton).toHaveAttribute('aria-expanded', 'true');
        const disclosureBoxAfter = await problemStatementButton.boundingBox();
        const triggerIslandBoxAfter = await problemStatementTriggerIsland.boundingBox();
        const panelBoxAfter = await problemStatementPanel.boundingBox();
        const explanationBoxAfterStatement = await bottomCenter.boundingBox();
        expect(disclosureBoxBefore).not.toBeNull();
        expect(disclosureBoxAfter).not.toBeNull();
        expect(triggerIslandBoxBefore).not.toBeNull();
        expect(triggerIslandBoxAfter).not.toBeNull();
        expect(panelBoxAfter).not.toBeNull();
        expect(explanationBoxBeforeStatement).not.toBeNull();
        expect(explanationBoxAfterStatement).not.toBeNull();
        expect(Math.abs(disclosureBoxBefore!.x + disclosureBoxBefore!.width - (disclosureBoxAfter!.x + disclosureBoxAfter!.width))).toBeLessThanOrEqual(1);
        expect(Math.abs(disclosureBoxBefore!.y - disclosureBoxAfter!.y)).toBeLessThanOrEqual(1);
        expect(Math.abs(panelBoxAfter!.y - (triggerIslandBoxAfter!.y + triggerIslandBoxAfter!.height - 2))).toBeLessThanOrEqual(1);
        expect(panelBoxAfter!.y).toBeGreaterThan(triggerIslandBoxAfter!.y);
        expect(Math.abs(explanationBoxBeforeStatement!.x - explanationBoxAfterStatement!.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxBeforeStatement!.y - explanationBoxAfterStatement!.y)).toBeLessThanOrEqual(1);
        await expect(horizontalProblemStatementResizer).toBeVisible();
        await expect(horizontalProblemStatementResizer).toHaveAttribute('role', 'separator');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-orientation', 'vertical');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-label', 'Resize problem statement');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-valuemin', '288');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-valuemax', '704');
        await expect(verticalProblemStatementResizer).toBeVisible();
        await expect(verticalProblemStatementResizer).toHaveAttribute('role', 'separator');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-orientation', 'horizontal');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-label', 'Resize problem statement');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-valuemin', '224');
        expect(
            await horizontalProblemStatementResizer.evaluate((handle) => ({
                cursor: getComputedStyle(handle).cursor,
                indicatorOpacity: getComputedStyle(handle, '::after').opacity,
                indicatorHeight: getComputedStyle(handle, '::after').height,
            })),
        ).toEqual({ cursor: 'col-resize', indicatorOpacity: '0.65', indicatorHeight: '60px' });
        expect(
            await verticalProblemStatementResizer.evaluate((handle) => ({
                cursor: getComputedStyle(handle).cursor,
                indicatorOpacity: getComputedStyle(handle, '::after').opacity,
                indicatorWidth: getComputedStyle(handle, '::after').width,
            })),
        ).toEqual({ cursor: 'row-resize', indicatorOpacity: '0.65', indicatorWidth: '60px' });

        const fullscreenDiagramTypeBox = await diagramTypeIsland.boundingBox();
        expect(fullscreenDiagramTypeBox).not.toBeNull();
        const paintedProblemStatementSurfaces = [problemStatementButton, problemStatementPanel];
        for (const surface of paintedProblemStatementSurfaces) {
            const surfaceBox = await surface.boundingBox();
            expect(surfaceBox).not.toBeNull();
            for (const cornerControl of [fullscreenActionsBox!, fullscreenDiagramTypeBox!]) {
                const overlap =
                    cornerControl.x < surfaceBox!.x + surfaceBox!.width &&
                    cornerControl.x + cornerControl.width > surfaceBox!.x &&
                    cornerControl.y < surfaceBox!.y + surfaceBox!.height &&
                    cornerControl.y + cornerControl.height > surfaceBox!.y;
                expect(overlap).toBe(false);
            }
        }

        const panelBoxBeforeResize = await problemStatementPanel.boundingBox();
        const resizerBox = await horizontalProblemStatementResizer.boundingBox();
        expect(panelBoxBeforeResize).not.toBeNull();
        expect(resizerBox).not.toBeNull();
        await page.mouse.move(resizerBox!.x + resizerBox!.width / 2, resizerBox!.y + resizerBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(resizerBox!.x - 80, resizerBox!.y + resizerBox!.height / 2, { steps: 12 });
        await page.mouse.up();
        const panelBoxAfterResize = await problemStatementPanel.boundingBox();
        const explanationBoxAfterResize = await bottomCenter.boundingBox();
        expect(panelBoxAfterResize).not.toBeNull();
        expect(explanationBoxAfterResize).not.toBeNull();
        expect(panelBoxAfterResize!.width).toBeGreaterThanOrEqual(panelBoxBeforeResize!.width + 70);
        expect(Math.abs(panelBoxBeforeResize!.x + panelBoxBeforeResize!.width - (panelBoxAfterResize!.x + panelBoxAfterResize!.width))).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxAfterStatement!.x - explanationBoxAfterResize!.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxAfterStatement!.y - explanationBoxAfterResize!.y)).toBeLessThanOrEqual(1);
        expect(await horizontalProblemStatementResizer.getAttribute('aria-valuenow')).toBe(`${Math.round(panelBoxAfterResize!.width)}`);

        const verticalResizerBox = await verticalProblemStatementResizer.boundingBox();
        expect(verticalResizerBox).not.toBeNull();
        expect(
            await verticalProblemStatementResizer.evaluate((handle) => {
                const rect = handle.getBoundingClientRect();
                const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
                return hit === handle || handle.contains(hit);
            }),
        ).toBe(true);
        await page.mouse.move(verticalResizerBox!.x + verticalResizerBox!.width / 2, verticalResizerBox!.y + verticalResizerBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(verticalResizerBox!.x + verticalResizerBox!.width / 2, verticalResizerBox!.y + 60, { steps: 12 });
        await page.mouse.up();
        const panelBoxAfterVerticalResize = await problemStatementPanel.boundingBox();
        const explanationBoxAfterVerticalResize = await bottomCenter.boundingBox();
        expect(panelBoxAfterVerticalResize).not.toBeNull();
        expect(explanationBoxAfterVerticalResize).not.toBeNull();
        expect(panelBoxAfterVerticalResize!.height).toBeGreaterThanOrEqual(panelBoxAfterResize!.height + 40);
        expect(Math.abs(panelBoxAfterVerticalResize!.y - panelBoxAfterResize!.y)).toBeLessThanOrEqual(1);
        expect(Math.abs(panelBoxAfterVerticalResize!.width - panelBoxAfterResize!.width)).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxAfterStatement!.x - explanationBoxAfterVerticalResize!.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxAfterStatement!.y - explanationBoxAfterVerticalResize!.y)).toBeLessThanOrEqual(1);
        expect(await verticalProblemStatementResizer.getAttribute('aria-valuenow')).toBe(`${Math.round(panelBoxAfterVerticalResize!.height)}`);
        const overlapsExplanationHorizontally =
            panelBoxAfterVerticalResize!.x < explanationBoxAfterVerticalResize!.x + explanationBoxAfterVerticalResize!.width &&
            panelBoxAfterVerticalResize!.x + panelBoxAfterVerticalResize!.width > explanationBoxAfterVerticalResize!.x;
        if (overlapsExplanationHorizontally) {
            expect(panelBoxAfterVerticalResize!.y + panelBoxAfterVerticalResize!.height).toBeLessThanOrEqual(explanationBoxAfterVerticalResize!.y - chromeGap + 1);
        }

        await page.evaluate(() => document.exitFullscreen());
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await page.setViewportSize({ width: 900, height: 900 });
        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);
        await expect(problemStatementPanel).toBeVisible();
        const narrowProblemBox = await problemStatementPanel.boundingBox();
        const narrowFrameBox = await fullscreenFrame.boundingBox();
        const narrowActionsBox = await actions.boundingBox();
        const narrowDiagramTypeBox = await diagramTypeIsland.boundingBox();
        expect(narrowProblemBox).not.toBeNull();
        expect(narrowFrameBox).not.toBeNull();
        expect(narrowActionsBox).not.toBeNull();
        expect(narrowDiagramTypeBox).not.toBeNull();
        expect(narrowProblemBox!.x).toBeGreaterThanOrEqual(narrowFrameBox!.x);
        expect(narrowProblemBox!.x + narrowProblemBox!.width).toBeLessThanOrEqual(narrowFrameBox!.x + narrowFrameBox!.width);
        expect(narrowProblemBox!.y - (narrowActionsBox!.y + narrowActionsBox!.height)).toBeGreaterThanOrEqual(chromeGap - 1);
        expect(narrowProblemBox!.x).toBeGreaterThan(narrowDiagramTypeBox!.x + narrowDiagramTypeBox!.width);
        await page.evaluate(() => document.exitFullscreen());
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await page.setViewportSize({ width: 1440, height: 1000 });
        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);
        await expect(fullscreenFrame.locator('[data-apollon-region="right-rail"] .modeling-editor__problem-statement')).toBeVisible();
    });

    test('keeps document-portaled interactions visible in fullscreen and restores the page on exit', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);

        const fullscreenFrame = page.locator('.modeling-editor__frame--fullscreen');
        await expect(fullscreenFrame).toBeVisible();
        await expect(fullscreenFrame.locator('.scroll-overlay')).toHaveCount(0);

        const paletteClass = page.getByRole('button', { name: 'Add element: Class' });
        const canvas = fullscreenFrame.locator('.react-flow');
        const paletteBox = await paletteClass.boundingBox();
        const canvasBox = await canvas.boundingBox();
        expect(paletteBox).not.toBeNull();
        expect(canvasBox).not.toBeNull();
        const bodyChildren = page.locator('body > *');
        const bodyChildCountBeforeDrag = await bodyChildren.count();
        const dragTarget = { x: canvasBox!.x + canvasBox!.width * 0.55, y: canvasBox!.y + canvasBox!.height * 0.5 };

        await page.mouse.move(paletteBox!.x + paletteBox!.width / 2, paletteBox!.y + paletteBox!.height / 2);
        await page.mouse.down();
        try {
            await page.mouse.move(dragTarget.x, dragTarget.y, { steps: 15 });
            await expect(bodyChildren).toHaveCount(bodyChildCountBeforeDrag + 1);
            const dragPreviewBox = await bodyChildren.last().boundingBox();
            expect(dragPreviewBox).not.toBeNull();
            expect(dragTarget.x).toBeGreaterThanOrEqual(dragPreviewBox!.x);
            expect(dragTarget.x).toBeLessThanOrEqual(dragPreviewBox!.x + dragPreviewBox!.width);
            expect(dragTarget.y).toBeGreaterThanOrEqual(dragPreviewBox!.y);
            expect(dragTarget.y).toBeLessThanOrEqual(dragPreviewBox!.y + dragPreviewBox!.height);
        } finally {
            await page.mouse.up();
        }

        const node = fullscreenFrame.locator('.react-flow__node').first();
        await expect(node).toBeVisible();
        await node.click();
        await page.getByRole('button', { name: 'Edit element' }).click();

        const popover = page.locator('.apollon-popover');
        await expect(popover).toBeVisible();
        const className = popover.getByRole('textbox', { name: 'Name', exact: true }).first();
        await className.fill('Fullscreen class');
        await expect(className).toHaveValue('Fullscreen class');

        await page.getByRole('button', { name: 'Help' }).click();
        const helpDialog = page.getByRole('dialog');
        await expect(helpDialog).toBeVisible();
        await expect(helpDialog.getByRole('button', { name: 'Close' })).toHaveCount(1);
        await expect(helpDialog.getByRole('tab', { name: 'Walkthrough' })).toBeVisible();
        await expect(helpDialog.locator('img[src*="apollon-help-"]').first()).toBeVisible();
        await helpDialog.getByRole('tab', { name: 'Keyboard shortcuts' }).click();
        await expect(helpDialog.getByText('Zoom to selection')).toBeVisible();

        await helpDialog.getByRole('button', { name: 'Close' }).click();
        await page.getByRole('button', { name: 'Exit Fullscreen' }).click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect(page.locator('.modeling-editor__frame--fullscreen')).toHaveCount(0);
    });

    test('separates the assessment rationale from the submission explanation in example assessments', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/8/example-submissions/1`);

        await dismissPasskeyReminderIfPresent(page);

        const readAndConfirmButton = page.getByRole('button', { name: 'Read and Confirm' });
        await readAndConfirmButton.click();
        await expect(readAndConfirmButton).toHaveAttribute('aria-pressed', 'true');
        const defineAssessmentButton = page.getByRole('button', { name: 'Define assessment' });
        await expect(defineAssessmentButton).toHaveAttribute('aria-pressed', 'false');
        await expect(defineAssessmentButton).toHaveAttribute('data-variant', 'ghost');
        await expect(defineAssessmentButton).toHaveAttribute('data-size', 'sm');
        await defineAssessmentButton.click();

        const assessment = page.locator('jhi-modeling-assessment');
        const workspace = page.locator('jhi-assessment-workspace');
        const rationale = workspace.locator('[assessmentWorkspaceDetails] .example-assessment-rationale');
        const submissionExplanation = assessment.locator('[data-apollon-region="bottom-center"] jhi-modeling-explanation-editor');
        const legend = assessment.locator('[data-apollon-region="top-right"] .assessment-legend');
        await expect(rationale).toBeVisible();
        await expect(rationale.getByRole('heading', { name: 'Assessment rationale' })).toBeVisible();
        await expect(rationale.locator('textarea')).toBeEditable();
        await expect(submissionExplanation).toBeVisible();
        await expect(submissionExplanation.locator('.modeling-explanation-surface__notch')).toContainText('Explanation');
        await expect(page.getByRole('button', { name: 'Edit model' })).toHaveAttribute('aria-pressed', 'false');
        await expect(page.getByRole('button', { name: 'Edit model' })).toHaveAttribute('data-variant', 'ghost');
        await expect(page.getByRole('button', { name: 'Define assessment' })).toHaveAttribute('aria-pressed', 'true');
        await expect(page.getByRole('button', { name: 'Define assessment' })).toHaveAttribute('data-variant', 'secondary');
        await expect(assessment.locator('.example-assessment-rationale')).toHaveCount(0);
        await expect(workspace.getByRole('heading', { name: 'Instructions' })).toBeVisible();
        await expect(workspace.getByRole('heading', { name: 'Feedback', exact: true })).toBeVisible();
        await expect
            .poll(() =>
                page.evaluate(() => ({
                    widthFits: document.documentElement.scrollWidth <= document.documentElement.clientWidth + 2,
                    heightFits: document.documentElement.scrollHeight <= document.documentElement.clientHeight + 2,
                })),
            )
            .toEqual({ widthFits: true, heightFits: true });

        const expectSeparated = async () => {
            const [assessmentBox, rationaleBox, submissionBox, legendBox] = await Promise.all([
                assessment.boundingBox(),
                rationale.boundingBox(),
                submissionExplanation.boundingBox(),
                legend.boundingBox(),
            ]);
            expect(assessmentBox).not.toBeNull();
            expect(rationaleBox).not.toBeNull();
            expect(submissionBox).not.toBeNull();
            expect(legendBox).not.toBeNull();
            expect(rationaleBox!.x).toBeGreaterThanOrEqual(assessmentBox!.x + assessmentBox!.width);
            expect(submissionBox!.x + submissionBox!.width).toBeLessThanOrEqual(assessmentBox!.x + assessmentBox!.width);
            expect(legendBox!.x + legendBox!.width).toBeLessThanOrEqual(assessmentBox!.x + assessmentBox!.width);
        };

        await expectSeparated();
        await page.setViewportSize({ width: 1200, height: 900 });
        await expect(rationale).toBeVisible();
        await expectSeparated();
    });
});
