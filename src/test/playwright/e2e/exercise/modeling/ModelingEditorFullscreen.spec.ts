import { expect, type Locator, type Page } from '@playwright/test';

import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';

import { admin, instructor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { ExerciseAPIRequests } from '../../../support/requests/ExerciseAPIRequests';
import { newBrowserPage } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';
import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';
import modelingExerciseSubmissionTemplate from '../../../fixtures/exercise/modeling/submission.json';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

const DEFAULT_VIEWPORT = { width: 1440, height: 1000 };
/** Box geometry is compared across elements the browser rounds independently, so exact edges never line up. */
const SUB_PIXEL_TOLERANCE = 1;
/** Once the surface is clamped to the frame rather than placed against a control, the clamp rounds a little wider. */
const CLAMPED_INSET_TOLERANCE = 3;
/** Enough growth to distinguish an expanded minimap from the collapsed button. */
const MINIMAP_EXPANDED_MIN_GROWTH = 100;
/** How far the explanation notch is dragged upwards when the resize is exercised. */
const EXPLANATION_RESIZE_DRAG = 55;

/** The gap Apollon leaves between the controls in an overlay corner, which is also the inset the chrome must respect. */
const chromeColumnGap = (page: Page) =>
    page
        .locator('.apollon-overlay-corner')
        .first()
        .evaluate((region) => Number.parseFloat(getComputedStyle(region).columnGap));

const expectReadOnlyDiagramToFit = async (editor: Locator) => {
    await editor.scrollIntoViewIfNeeded();
    await expect(editor.locator('.modeling-editor__frame')).toHaveCount(0);
    const svg = editor.locator('.readonly-diagram > svg');
    await expect(svg).toBeVisible();
    const geometry = await svg.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        const viewBox = element.viewBox.baseVal;
        return {
            renderedRatio: bounds.width / bounds.height,
            sourceRatio: viewBox.width / viewBox.height,
            height: bounds.height,
            maxHeight: Math.min(window.innerHeight * 0.7, 800),
        };
    });
    expect(Math.abs(geometry.renderedRatio - geometry.sourceRatio)).toBeLessThan(0.02);
    expect(geometry.height).toBeLessThanOrEqual(geometry.maxHeight + 1);

    const tallGeometry = await svg.evaluate((element) => {
        const original = {
            width: element.getAttribute('width'),
            height: element.getAttribute('height'),
            viewBox: element.getAttribute('viewBox'),
        };
        element.setAttribute('width', '400');
        element.setAttribute('height', '1600');
        element.setAttribute('viewBox', '0 0 400 1600');
        const bounds = element.getBoundingClientRect();
        const containerWidth = element.parentElement!.getBoundingClientRect().width;
        for (const [name, value] of Object.entries(original)) {
            if (value === null) {
                element.removeAttribute(name);
            } else {
                element.setAttribute(name, value);
            }
        }
        return { ratio: bounds.width / bounds.height, height: bounds.height, maxHeight: Math.min(window.innerHeight * 0.7, 800), width: bounds.width, containerWidth };
    });
    expect(Math.abs(tallGeometry.ratio - 0.25)).toBeLessThan(0.02);
    expect(tallGeometry.height).toBeLessThanOrEqual(tallGeometry.maxHeight + 1);
    expect(tallGeometry.width).toBeLessThan(tallGeometry.containerWidth);
};

test.describe('Fullscreen modeling editor', { tag: '@fast' }, () => {
    test.use({ viewport: DEFAULT_VIEWPORT });

    // Most tests here work on the creation form and need nothing persisted. The three that open an existing exercise
    // or example submission do — and the E2E seed provisions no exercises, so they are created here rather than
    // addressed by guessed ids.
    let existingExercise: ModelingExercise;
    let exampleSubmissionId: number;

    test.beforeAll('Create a modeling exercise with an example submission', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        const exerciseAPIRequests = new ExerciseAPIRequests(page);

        await Commands.login(page, admin);
        // The exercise template carries an example solution model, which is what the read-only detail diagram renders.
        existingExercise = await exerciseAPIRequests.createModelingExercise({ course });

        // The submission template has no relationships, but one test opens assessment feedback on a relationship, so
        // the model gets an association between two of its classes.
        const model = JSON.parse(modelingExerciseSubmissionTemplate.model);
        model.relationships.push({
            id: 'e6ea1e64-9f0a-4a2b-8f0e-2a4a1c5d6e70',
            type: 'ClassUnidirectional',
            name: '',
            owner: null,
            bounds: { x: 100, y: 232, width: 240, height: 198 },
            path: [
                { x: 100, y: 0 },
                { x: 100, y: 198 },
                { x: 240, y: 198 },
            ],
            source: { direction: 'Down', element: '8786cede-a45c-4593-ac6a-b7ac235f1fae' },
            target: { direction: 'Left', element: 'ccf5edad-98d5-401d-9885-e332607dfa6a' },
            isManuallyLayouted: false,
        });

        const exampleSubmissionResponse = await page.request.post(`api/assessment/exercises/${existingExercise.id}/example-submissions`, {
            data: {
                exercise: existingExercise,
                // Read-and-confirm is the mode the assessment-feedback test starts from.
                usedForTutorial: false,
                submission: {
                    ...modelingExerciseSubmissionTemplate,
                    model: JSON.stringify(model),
                    // The bottom-center region only mounts when there is an explanation, and one test asserts the
                    // submission explanation sits there, separate from the assessment rationale.
                    explanationText: 'The abstract class captures the shared behaviour of both concrete classes.',
                    id: null,
                    participation: null,
                    exampleSubmission: true,
                },
            },
        });
        expect(exampleSubmissionResponse.ok()).toBe(true);
        exampleSubmissionId = (await exampleSubmissionResponse.json()).id;
    });

    test('renders the Artemis chrome actions as native Apollon controls', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);
        await expect(page.locator('[data-testid="apollon-palette"]').first()).toBeVisible();

        const actions = page.locator('.modeling-editor__actions');
        const diagramTypeIsland = page.locator('[data-apollon-region="top-left"] .modeling-editor__top-left');
        const diagramTypeSelect = diagramTypeIsland.getByRole('combobox', { name: 'Diagram Type' });
        await expect(diagramTypeSelect).toContainText('Class Diagram');

        const diagramTypeBox = (await diagramTypeIsland.boundingBox())!;
        const actionsBox = (await actions.boundingBox())!;
        expect(diagramTypeBox.x + diagramTypeBox.width).toBeLessThanOrEqual(actionsBox.x);

        // Switching the diagram type tears Apollon down and remounts it, which is the one way the hosted regions can
        // be lost. Operability is read from the value changing rather than from focus, which Apollon moves while mounting.
        await diagramTypeSelect.scrollIntoViewIfNeeded();
        await diagramTypeSelect.click();
        await page.getByRole('option', { name: 'Activity Diagram' }).click();
        await expect(diagramTypeSelect).toContainText('Activity Diagram');
        await expect(diagramTypeIsland).toBeVisible();
        await expect(actions).toBeVisible();

        const helpButton = page.getByRole('button', { name: 'Help' });
        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        const zoomOutButton = page.locator('[data-apollon-control="apollon:zoom"] .apollon-chrome-iconbtn').first();
        await expect(zoomOutButton).toBeVisible();
        for (const button of [helpButton, fullscreenButton]) {
            await expect(button).toHaveClass(/artemis-apollon-chrome-action/);
            await expect(button).toHaveClass(/apollon-chrome-iconbtn/);
            await expect(button).not.toHaveAttribute('data-slot');
        }

        // Artemis' own actions must be indistinguishable from Apollon's controls, apart from the icon-to-label gap
        // that only the labelled ones need.
        const restingAppearance = (button: Locator) =>
            button.evaluate((element) => {
                const style = getComputedStyle(element);
                return { backgroundColor: style.backgroundColor, borderRadius: style.borderRadius, color: style.color, gap: style.gap, height: style.height };
            });
        const { gap: zoomGap, ...zoomAppearance } = await restingAppearance(zoomOutButton);
        expect(zoomGap).toBe('normal');
        for (const labelledButton of [helpButton, fullscreenButton]) {
            const { gap, ...labelledAppearance } = await restingAppearance(labelledButton);
            expect(gap).not.toBe('normal');
            expect(labelledAppearance).toEqual(zoomAppearance);
        }
    });

    test('mounts the problem statement in the rail and the example explanation in the bottom-center chrome', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);
        await expect(page.locator('[data-testid="apollon-palette"]').first()).toBeVisible();
        await expect(page.locator('jhi-apollon-rail-disclosure')).toBeHidden();

        const problemStatementEditor = page.locator('#field_problemStatement .monaco-editor');
        await problemStatementEditor.click();
        await page.keyboard.insertText('## Design task\n\nModel a library with books and authors.');
        await expect(page.locator('.apollon-rail-disclosure__panel')).toContainText('Design task');

        const bottomCenter = page.locator('.modeling-exercise-example-explanation');
        const surface = bottomCenter.locator('.modeling-explanation-surface__surface');
        const notch = bottomCenter.locator('.modeling-explanation-surface__notch');
        const editor = page.locator('.modeling-markdown-explanation-editor__editor');
        const chromeGap = await chromeColumnGap(page);
        await expect(surface).toBeVisible();
        await expect(notch).toContainText('Example Solution Explanation');
        await bottomCenter.scrollIntoViewIfNeeded();

        const surfaceBox = (await surface.boundingBox())!;
        const editorBox = (await editor.boundingBox())!;
        // The editor sits fully inside the surface on all four sides, which is also what proves the surface is not collapsed.
        expect(editorBox.x).toBeGreaterThan(surfaceBox.x);
        expect(editorBox.x + editorBox.width).toBeLessThan(surfaceBox.x + surfaceBox.width);
        expect(editorBox.y).toBeGreaterThan(surfaceBox.y);
        expect(editorBox.y + editorBox.height).toBeLessThan(surfaceBox.y + surfaceBox.height);

        const minimap = page.locator('[data-apollon-control="apollon:minimap"]');
        const minimapBox = (await minimap.boundingBox())!;
        const [paletteBox, zoomBox] = await Promise.all([
            page.locator('[data-apollon-control="apollon:palette"]').boundingBox(),
            page.locator('[data-apollon-control="apollon:zoom"]').boundingBox(),
        ]);
        expect(surfaceBox.x).toBeGreaterThanOrEqual(Math.max(paletteBox!.x + paletteBox!.width, zoomBox!.x + zoomBox!.width) + chromeGap - SUB_PIXEL_TOLERANCE);
        expect(surfaceBox.x + surfaceBox.width).toBeLessThanOrEqual(minimapBox.x - chromeGap + SUB_PIXEL_TOLERANCE);
        await expect(page.locator('[data-apollon-region="bottom-center"] .modeling-editor__bottom-center:not(.modeling-editor__bottom-center--elevated)')).toBeVisible();

        // The label must fit its notch; an ellipsis here means the notch stopped tracking the surface width.
        expect(await notch.locator('span').evaluate((label) => label.scrollWidth <= label.clientWidth + 1)).toBe(true);

        // Expanding the minimap has to push the surface back, and collapsing it has to hand the room back.
        await minimap.getByRole('button', { name: 'Show minimap' }).click();
        await expect(minimap.getByRole('button', { name: 'Hide minimap' })).toBeVisible();
        await expect.poll(async () => (await minimap.boundingBox())!.width).toBeGreaterThan(minimapBox.width + MINIMAP_EXPANDED_MIN_GROWTH);
        await expect
            .poll(async () => {
                const [reflowed, expandedMap] = await Promise.all([surface.boundingBox(), minimap.boundingBox()]);
                return reflowed!.x + reflowed!.width <= expandedMap!.x - chromeGap + SUB_PIXEL_TOLERANCE;
            })
            .toBe(true);
        await minimap.getByRole('button', { name: 'Hide minimap' }).click();
        await expect
            .poll(async () => {
                const restored = (await surface.boundingBox())!;
                return { x: Math.round(restored.x), width: Math.round(restored.width) };
            })
            .toEqual({ x: Math.round(surfaceBox.x), width: Math.round(surfaceBox.width) });
    });

    test('keeps the example explanation clear of the chrome when it is resized or the viewport shrinks', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);
        await expect(page.locator('[data-testid="apollon-palette"]').first()).toBeVisible();

        const editorFrame = page.locator('.modeling-editor__frame');
        const bottomCenter = page.locator('.modeling-exercise-example-explanation');
        const surface = bottomCenter.locator('.modeling-explanation-surface__surface');
        const notch = bottomCenter.locator('.modeling-explanation-surface__notch');
        const palette = page.locator('[data-apollon-control="apollon:palette"]');
        const zoom = page.locator('[data-apollon-control="apollon:zoom"]');
        const minimap = page.locator('[data-apollon-control="apollon:minimap"]');
        const canvasViewport = page.locator('.modeling-editor__frame .react-flow__viewport');
        const chromeGap = await chromeColumnGap(page);
        await expect(surface).toBeVisible();
        await bottomCenter.scrollIntoViewIfNeeded();

        const frameBefore = (await editorFrame.boundingBox())!;
        const zoomBefore = (await zoom.boundingBox())!;
        const minimapBefore = (await minimap.boundingBox())!;
        const surfaceBefore = (await surface.boundingBox())!;
        const canvasTransformBefore = await canvasViewport.evaluate((viewport) => getComputedStyle(viewport).transform);

        // The markdown editor takes the browser fullscreen from inside the surface; leaving it must not resize the surface.
        const markdownFullscreenButton = page.locator('.modeling-markdown-explanation-editor__editor .md-action-palette .md-toolbar-btn').last();
        await markdownFullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement?.classList.contains('h-full'))).toBe(true);
        await markdownFullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect.poll(async () => (await surface.boundingBox())!.width).toBeLessThanOrEqual(surfaceBefore.width + SUB_PIXEL_TOLERANCE);

        // The notch doubles as the resize handle, so nothing may sit on top of it.
        const notchBox = (await notch.boundingBox())!;
        const hitTarget = await notch.evaluate((element) => {
            const rect = element.getBoundingClientRect();
            const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
            return { isNotch: element.contains(hit), tagName: hit?.tagName, className: hit?.getAttribute('class') };
        });
        expect(hitTarget.isNotch, JSON.stringify(hitTarget)).toBe(true);
        await page.mouse.move(notchBox.x + notchBox.width / 2, notchBox.y + notchBox.height / 2);
        await page.mouse.down();
        await page.mouse.move(notchBox.x + notchBox.width / 2, notchBox.y - EXPLANATION_RESIZE_DRAG, { steps: 6 });
        await page.mouse.up();
        await expect(surface).toHaveClass(/modeling-explanation-surface__surface--manually-sized/);
        await expect.poll(async () => (await surface.boundingBox())!.height).toBeGreaterThan(surfaceBefore.height + EXPLANATION_RESIZE_DRAG * 0.7);

        // Narrow enough that the surface has to give way to the side controls, then narrow enough that it can only
        // clear the palette and must clamp to the frame instead.
        await page.setViewportSize({ width: 900, height: 900 });
        await expect(bottomCenter).toBeVisible();
        await expect
            .poll(async () => {
                const [surfaceBox, paletteBox, zoomBox, minimapBox] = await Promise.all([surface.boundingBox(), palette.boundingBox(), zoom.boundingBox(), minimap.boundingBox()]);
                const clearsSideControls = Math.max(paletteBox!.x + paletteBox!.width, zoomBox!.x + zoomBox!.width) + chromeGap - SUB_PIXEL_TOLERANCE;
                return surfaceBox!.x >= clearsSideControls && surfaceBox!.x + surfaceBox!.width <= minimapBox!.x - chromeGap + SUB_PIXEL_TOLERANCE;
            })
            .toBe(true);

        await page.setViewportSize({ width: 640, height: 900 });
        await expect
            .poll(async () => {
                const [frameBox, surfaceBox, paletteBox] = await Promise.all([editorFrame.boundingBox(), surface.boundingBox(), palette.boundingBox()]);
                return (
                    surfaceBox!.x >= frameBox!.x + chromeGap - CLAMPED_INSET_TOLERANCE &&
                    surfaceBox!.x + surfaceBox!.width <= frameBox!.x + frameBox!.width - chromeGap + CLAMPED_INSET_TOLERANCE &&
                    surfaceBox!.x >= paletteBox!.x + paletteBox!.width + chromeGap - SUB_PIXEL_TOLERANCE
                );
            })
            .toBe(true);
        const [compactSurfaceBox, compactEditorBox] = await Promise.all([surface.boundingBox(), page.locator('.modeling-markdown-explanation-editor__editor').boundingBox()]);
        expect(compactEditorBox!.x).toBeGreaterThan(compactSurfaceBox!.x);
        expect(compactEditorBox!.x + compactEditorBox!.width).toBeLessThan(compactSurfaceBox!.x + compactSurfaceBox!.width);

        // Back where it started: resizing must not have panned the canvas or moved the controls off the frame's edge.
        await page.setViewportSize(DEFAULT_VIEWPORT);
        await expect.poll(() => canvasViewport.evaluate((viewport) => getComputedStyle(viewport).transform)).toBe(canvasTransformBefore);
        const bottomInset = (frame: { y: number; height: number }, control: { y: number; height: number }) => frame.y + frame.height - (control.y + control.height);
        const frameAfter = (await editorFrame.boundingBox())!;
        for (const [before, after] of [
            [zoomBefore, (await zoom.boundingBox())!],
            [minimapBefore, (await minimap.boundingBox())!],
        ]) {
            expect(Math.abs(bottomInset(frameBefore, before) - bottomInset(frameAfter, after))).toBeLessThanOrEqual(SUB_PIXEL_TOLERANCE);
        }
    });

    /**
     * The opt-in's state machine is covered by `ExerciseUpdateTimelineComponent`'s unit spec. What only the real page
     * can show is the two things asserted here: the opt-in tracks the live example solution across two components,
     * and the picker it reveals stays a compact control instead of stretching across the form.
     */
    test('enables the publication opt-in from the live example solution and keeps its picker compact', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const toggle = page.getByTestId('example-solution-publication-toggle');
        await toggle.scrollIntoViewIfNeeded();
        await expect(toggle).toBeDisabled();

        // The explanation counts as example solution content just as a drawn diagram does, and typing it
        // does not depend on canvas pointer mechanics.
        await page.locator('.modeling-exercise-example-explanation .monaco-editor').click();
        await page.keyboard.insertText('The solution models a library.');

        await expect(toggle).toBeEnabled();
        await toggle.check();
        const publicationRow = page.locator('.timeline-item-row', { hasText: 'Example Solution Publication Date' });
        await expect(publicationRow).toHaveCount(1);

        const trigger = publicationRow.locator('p-datepicker');
        await trigger.scrollIntoViewIfNeeded();
        await expect(trigger).not.toHaveClass(/p-datepicker-fluid/);
        const triggerBox = await trigger.boundingBox();
        expect(triggerBox).not.toBeNull();
        expect(triggerBox!.width).toBeLessThanOrEqual(384);

        await publicationRow.locator('.p-datepicker-input-icon-container').click();
        const panel = page.locator('.p-datepicker-panel');
        await expect(panel).toBeVisible();
        const panelBox = await panel.boundingBox();
        expect(panelBox).not.toBeNull();
        expect(panelBox!.width).toBeLessThanOrEqual(384);
    });

    test('keeps the fullscreen problem statement stable while toggling and resizing it', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const actions = page.locator('.modeling-editor__actions');
        const diagramTypeIsland = page.locator('[data-apollon-region="top-left"] .modeling-editor__top-left');
        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        const problemStatementEditor = page.locator('#field_problemStatement .monaco-editor');
        await problemStatementEditor.click();
        await page.keyboard.insertText('## Design task\n\nModel a library with books and authors.');
        await expect(page.locator('.apollon-rail-disclosure__panel')).toContainText('Design task');

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
        // Fullscreen releases the scroll lock, so the hint must never be shown even though its root stays mounted.
        await expect(fullscreenFrame.locator('.scroll-overlay--visible')).toHaveCount(0);

        const problemStatementIsland = fullscreenFrame.locator('[data-apollon-region="right-rail"] jhi-apollon-rail-disclosure');
        const problemStatementPanel = problemStatementIsland.locator('.apollon-rail-disclosure__panel');
        const problemStatementButton = problemStatementIsland.getByTestId('modeling-editor-problem-statement');
        const problemStatementTriggerIsland = problemStatementIsland.locator('.apollon-rail-disclosure__trigger-island');
        const horizontalProblemStatementResizer = problemStatementIsland.locator('.apollon-rail-disclosure__resizer--left');
        const verticalProblemStatementResizer = problemStatementIsland.locator('.apollon-rail-disclosure__resizer--bottom');
        await expect(problemStatementIsland).toBeVisible();
        await expect(problemStatementButton).toBeVisible();
        await expect(problemStatementButton).toHaveClass(/artemis-apollon-chrome-action/);
        await expect(problemStatementButton).toHaveClass(/apollon-chrome-iconbtn/);
        await expect(problemStatementButton).not.toHaveAttribute('data-slot');
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
        const paletteSurface = fullscreenFrame.locator('.apollon-palette');
        const explanationSurface = bottomCenter.locator('.modeling-explanation-surface__surface');
        const [problemStatementChrome, paletteChrome, explanationChrome, expandedTriggerChrome, explanationHandleBorderRadius] = await Promise.all([
            problemStatementPanel.evaluate((panel) => {
                const style = getComputedStyle(panel);
                return {
                    borderWidths: [style.borderTopWidth, style.borderRightWidth, style.borderBottomWidth, style.borderLeftWidth],
                    borderColor: style.borderTopColor,
                    boxShadow: style.boxShadow,
                };
            }),
            paletteSurface.evaluate((surface) => {
                const style = getComputedStyle(surface);
                return { borderWidth: style.borderTopWidth, borderColor: style.borderTopColor, boxShadow: style.boxShadow };
            }),
            explanationSurface.evaluate((surface) => {
                const style = getComputedStyle(surface);
                return { borderWidth: style.borderTopWidth, borderColor: style.borderTopColor, boxShadow: style.boxShadow };
            }),
            problemStatementTriggerIsland.evaluate((trigger) => {
                const style = getComputedStyle(trigger);
                return { borderWidth: style.borderTopWidth, borderColor: style.borderTopColor, boxShadow: style.boxShadow };
            }),
            bottomCenter.locator('.modeling-explanation-surface__resizer').evaluate((handle) => getComputedStyle(handle, '::after').borderRadius),
        ]);
        // Relational, not literal: every chrome surface has to read as the same material as the palette.
        expect(new Set(problemStatementChrome.borderWidths).size).toBe(1);
        expect(problemStatementChrome.borderWidths[0]).toBe(paletteChrome.borderWidth);
        expect(problemStatementChrome.borderColor).toBe(paletteChrome.borderColor);
        expect(problemStatementChrome.boxShadow).toBe(paletteChrome.boxShadow);
        expect(explanationChrome).toEqual(paletteChrome);
        expect(explanationChrome.borderWidth).toBe(problemStatementChrome.borderWidths[0]);
        expect(expandedTriggerChrome.borderWidth).toBe(paletteChrome.borderWidth);
        expect(expandedTriggerChrome.borderColor).toBe(paletteChrome.borderColor);
        expect(expandedTriggerChrome.boxShadow).not.toBe('none');
        expect(Math.abs(explanationBoxBeforeStatement!.x - explanationBoxAfterStatement!.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(explanationBoxBeforeStatement!.y - explanationBoxAfterStatement!.y)).toBeLessThanOrEqual(1);
        await expect(horizontalProblemStatementResizer).toBeVisible();
        await expect(horizontalProblemStatementResizer).toHaveAttribute('role', 'separator');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-orientation', 'vertical');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-label', 'Resize Problem Statement');
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-valuemin', /\d+/);
        await expect(horizontalProblemStatementResizer).toHaveAttribute('aria-valuemax', /\d+/);
        await expect(verticalProblemStatementResizer).toBeVisible();
        await expect(verticalProblemStatementResizer).toHaveAttribute('role', 'separator');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-orientation', 'horizontal');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-label', 'Resize Problem Statement');
        await expect(verticalProblemStatementResizer).toHaveAttribute('aria-valuemin', /\d+/);
        // Both handles have to read as the same affordance as the explanation surface's.
        expect(
            await horizontalProblemStatementResizer.evaluate((handle) => ({
                cursor: getComputedStyle(handle).cursor,
                indicatorBorderRadius: getComputedStyle(handle, '::after').borderRadius,
            })),
        ).toEqual({ cursor: 'col-resize', indicatorBorderRadius: explanationHandleBorderRadius });
        expect(
            await verticalProblemStatementResizer.evaluate((handle) => ({
                cursor: getComputedStyle(handle).cursor,
                indicatorBorderRadius: getComputedStyle(handle, '::after').borderRadius,
            })),
        ).toEqual({ cursor: 'row-resize', indicatorBorderRadius: explanationHandleBorderRadius });
        const handlePlacement = await problemStatementPanel.evaluate((panel) => {
            const panelBounds = panel.getBoundingClientRect();
            const leftHandle = panel.querySelector<HTMLElement>('.apollon-rail-disclosure__resizer--left')!;
            const bottomHandle = panel.querySelector<HTMLElement>('.apollon-rail-disclosure__resizer--bottom')!;
            const leftBounds = leftHandle.getBoundingClientRect();
            const bottomBounds = bottomHandle.getBoundingClientRect();
            return {
                leftCenterOffset: Math.abs(leftBounds.left + leftBounds.width / 2 - panelBounds.left),
                bottomCenterOffset: Math.abs(bottomBounds.top + bottomBounds.height / 2 - panelBounds.bottom),
                leftOutsideHit: document.elementFromPoint(panelBounds.left - 3, panelBounds.top + panelBounds.height / 2) === leftHandle,
                bottomOutsideHit: document.elementFromPoint(panelBounds.left + panelBounds.width / 2, panelBounds.bottom + 3) === bottomHandle,
            };
        });
        expect(handlePlacement.leftCenterOffset).toBeLessThanOrEqual(0.5);
        expect(handlePlacement.bottomCenterOffset).toBeLessThanOrEqual(0.5);
        expect(handlePlacement.leftOutsideHit).toBe(true);
        expect(handlePlacement.bottomOutsideHit).toBe(true);

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
        await page.setViewportSize(DEFAULT_VIEWPORT);
        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);
        await expect(fullscreenFrame.locator('[data-apollon-region="right-rail"] jhi-apollon-rail-disclosure')).toBeVisible();
    });

    test('keeps document-portaled interactions visible in fullscreen and restores the page on exit', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/new`);

        const fullscreenButton = page.getByTestId('modeling-editor-fullscreen');
        await fullscreenButton.click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);

        const fullscreenFrame = page.locator('.modeling-editor__frame--fullscreen');
        await expect(fullscreenFrame).toBeVisible();
        // Fullscreen releases the scroll lock, so the hint must never be shown even though its root stays mounted.
        await expect(fullscreenFrame.locator('.scroll-overlay--visible')).toHaveCount(0);

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
        const expectPopoverAboveEditor = async () =>
            expect
                .poll(() =>
                    popover.evaluate((element) => {
                        const bounds = element.getBoundingClientRect();
                        const hit = document.elementFromPoint(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
                        return hit === element || (hit !== null && element.contains(hit));
                    }),
                )
                .toBe(true);
        await expectPopoverAboveEditor();
        const className = popover.getByRole('textbox', { name: 'Name', exact: true }).first();
        await className.fill('Fullscreen class');
        await expect(className).toHaveValue('Fullscreen class');
        await page.keyboard.press('Escape');
        await expect(popover).toBeHidden();

        const secondNodeTarget = { x: canvasBox!.x + canvasBox!.width * 0.75, y: canvasBox!.y + canvasBox!.height * 0.5 };
        await page.mouse.move(paletteBox!.x + paletteBox!.width / 2, paletteBox!.y + paletteBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(secondNodeTarget.x, secondNodeTarget.y, { steps: 15 });
        await page.mouse.up();

        const nodes = fullscreenFrame.locator('.react-flow__node');
        await expect(nodes).toHaveCount(2);
        const sourceNode = nodes.first();
        const targetNode = nodes.nth(1);
        await sourceNode.hover();
        const sourceHandle = await sourceNode.locator('.apollon-arc-handle--right').nth(1).boundingBox();
        const targetHandle = await targetNode.locator('.apollon-arc-handle--left').nth(1).boundingBox();
        expect(sourceHandle).not.toBeNull();
        expect(targetHandle).not.toBeNull();
        await page.mouse.move(sourceHandle!.x + sourceHandle!.width / 2, sourceHandle!.y + sourceHandle!.height / 2);
        await page.mouse.down();
        await page.mouse.move(targetHandle!.x + targetHandle!.width / 2, targetHandle!.y + targetHandle!.height / 2, { steps: 20 });
        await page.mouse.up();

        const edge = fullscreenFrame.locator('.react-flow__edge').first();
        await expect(edge).toBeVisible();
        await edge.dispatchEvent('click');
        await page.getByRole('button', { name: 'Edit edge' }).click();
        await expect(popover).toBeVisible();
        await expectPopoverAboveEditor();
        await page.keyboard.press('Escape');

        await page.getByRole('button', { name: 'Help' }).click();
        const helpDialog = page.getByRole('dialog');
        await expect(helpDialog).toBeVisible();
        await expect(helpDialog.getByRole('button', { name: 'Close' })).toHaveCount(1);
        await expect(helpDialog.getByRole('tab', { name: 'Walkthrough' })).toBeVisible();
        const walkthroughImages = helpDialog.locator('.modeling-editor-help__image img');
        await expect(walkthroughImages).toHaveCount(6);
        await expect(walkthroughImages.first()).toHaveAttribute('src', /modeling-help\/create-element-light\.png$/);
        await expect(walkthroughImages.first()).toBeVisible();
        const lightImageSources = await walkthroughImages.evaluateAll((images) => images.map((image) => image.getAttribute('src')!));
        const imageResponses = await Promise.all(
            [...lightImageSources, ...lightImageSources.map((source) => source.replace('-light.png', '-dark.png'))].map((source) => page.request.get(source)),
        );
        expect(imageResponses.every((response) => response.ok())).toBe(true);
        await helpDialog.getByRole('tab', { name: 'Keyboard shortcuts' }).click();
        await expect(helpDialog.getByText('Zoom to selection')).toBeVisible();

        await helpDialog.getByRole('button', { name: 'Close' }).click();
        await page.getByRole('button', { name: 'Exit Fullscreen' }).click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect(page.locator('.modeling-editor__frame--fullscreen')).toHaveCount(0);
    });

    test('separates the assessment rationale from the submission explanation in example assessments', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/${existingExercise.id}/example-submissions/${exampleSubmissionId}`);

        await dismissPasskeyReminderIfPresent(page);

        const editWorkspace = page.locator('.example-submission-edit-split');
        // `exact`, or this also matches the collapsible "Assessment Instructions" header in the same pane.
        await expect(editWorkspace.getByRole('heading', { name: 'Instructions', exact: true })).toBeVisible();
        const editEditor = editWorkspace.locator('jhi-modeling-editor');
        await editEditor.getByTestId('modeling-editor-fullscreen').click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);
        await expect(page.locator('.modeling-editor__frame--fullscreen').getByTestId('modeling-editor-problem-statement')).toHaveAttribute('aria-expanded', 'true');
        await expect(page.locator('.modeling-editor__frame--fullscreen .apollon-rail-disclosure__panel')).toBeVisible();
        await page.getByRole('button', { name: 'Exit Fullscreen' }).click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();

        const readAndConfirmButton = page.getByRole('button', { name: 'Read and Confirm' });
        await readAndConfirmButton.click();
        await expect(readAndConfirmButton).toHaveAttribute('aria-pressed', 'true');
        const defineAssessmentButton = page.getByRole('button', { name: 'Define assessment' });
        await expect(defineAssessmentButton).toHaveAttribute('aria-pressed', 'false');
        await expect(defineAssessmentButton).toHaveClass(/artemis-apollon-chrome-action/);
        await expect(defineAssessmentButton).toHaveClass(/apollon-chrome-iconbtn/);
        await expect(defineAssessmentButton).toHaveClass(/apollon-chrome-iconbtn--toggle/);
        await expect(defineAssessmentButton).not.toHaveAttribute('data-slot');
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
        await expect(page.getByRole('button', { name: 'Edit model' })).toHaveClass(/apollon-chrome-iconbtn--toggle/);
        await expect(page.getByRole('button', { name: 'Define assessment' })).toHaveAttribute('aria-pressed', 'true');
        await expect(page.getByRole('button', { name: 'Define assessment' })).toHaveClass(/apollon-chrome-iconbtn--toggle/);
        await expect(assessment.locator('.example-assessment-rationale')).toHaveCount(0);
        await expect(workspace.getByRole('heading', { name: 'Instructions', exact: true })).toBeVisible();
        await expect(workspace.getByRole('heading', { name: 'Feedback', exact: true })).toBeVisible();
        await expectReadOnlyDiagramToFit(workspace.locator('[assessmentWorkspaceInstructions] jhi-modeling-editor'));
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
            const assessmentRight = assessmentBox!.x + assessmentBox!.width;
            expect(rationaleBox!.x).toBeGreaterThanOrEqual(assessmentRight - SUB_PIXEL_TOLERANCE);
            expect(submissionBox!.x + submissionBox!.width).toBeLessThanOrEqual(assessmentRight + SUB_PIXEL_TOLERANCE);
            expect(legendBox!.x + legendBox!.width).toBeLessThanOrEqual(assessmentRight + SUB_PIXEL_TOLERANCE);
        };

        await expectSeparated();
        await page.setViewportSize({ width: 1200, height: 900 });
        await expect(rationale).toBeVisible();
        await expectSeparated();
    });

    test('opens assessment feedback for relationships', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/${existingExercise.id}/example-submissions/${exampleSubmissionId}`);
        await dismissPasskeyReminderIfPresent(page);

        await page.getByRole('button', { name: 'Read and Confirm' }).click();
        await page.getByRole('button', { name: 'Define assessment' }).click();

        const assessment = page.locator('jhi-modeling-assessment');
        const openRelationshipFeedback = async () => {
            const edgeHitTarget = assessment.locator('.react-flow__edge .edge-overlay').first();
            await expect(edgeHitTarget).toBeAttached();
            const edgeCenter = await edgeHitTarget.evaluate((element) => {
                const path = element as SVGPathElement;
                const point = path.getPointAtLength(path.getTotalLength() / 2).matrixTransform(path.getScreenCTM()!);
                return { x: point.x, y: point.y };
            });
            await page.mouse.dblclick(edgeCenter.x, edgeCenter.y);
            const popover = page.locator('.apollon-popover');
            await expect(popover).toBeVisible();
            await expect(popover.getByRole('spinbutton', { name: 'Points' })).toBeEditable();
            await expect(popover.getByRole('textbox', { name: 'Feedback' })).toBeEditable();
        };

        const node = assessment.locator('.react-flow__node').first();
        await node.dblclick();
        await expect(page.locator('.apollon-popover')).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.locator('.apollon-popover')).toBeHidden();

        await openRelationshipFeedback();
    });

    test('fits the read-only example solution to the management detail viewport', async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/modeling-exercises/${existingExercise.id}`);
        await dismissPasskeyReminderIfPresent(page);

        await expectReadOnlyDiagramToFit(
            page
                .locator('jhi-modeling-editor')
                .filter({ has: page.locator('.readonly-diagram') })
                .first(),
        );
    });
});
