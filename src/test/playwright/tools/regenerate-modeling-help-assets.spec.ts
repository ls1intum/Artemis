import { expect, type Locator, type Page } from '@playwright/test';
import fs from 'fs/promises';
import path from 'path';

import { test } from '../support/fixtures';
import { instructor } from '../support/users';
import { dismissPasskeyReminderIfPresent } from '../support/dismissPasskeyReminder';
import { SEED_COURSES } from '../support/seedData';

const regenerateAssets = process.env.UPDATE_MODELING_HELP_SCREENSHOTS === '1';
const assetDirectory = path.resolve(__dirname, '../../../../../..', 'src/main/webapp/content/images/modeling-help');
const courseId = SEED_COURSES.exerciseManagement.id;
const themes = ['light', 'dark'] as const;
const assetNames = ['create-element', 'create-relationship', 'update-element', 'move-element', 'resize-element', 'reconnect-relationship'] as const;

type Theme = 'light' | 'dark';
type Point = { x: number; y: number };
type ScreenshotBox = { x: number; y: number; width: number; height: number };
type MinimumScreenshotSize = { width: number; height: number };

const center = (box: ScreenshotBox) => ({ x: box.x + box.width / 2, y: box.y + box.height / 2 });

const requireBox = async (locator: Locator): Promise<ScreenshotBox> => {
    const box = await locator.boundingBox();
    expect(box).not.toBeNull();
    return box!;
};

const addGestureAnnotation = async (page: Page, annotation: { from?: Point; via?: readonly Point[]; to: Point; outline?: ScreenshotBox }): Promise<void> => {
    await page.evaluate(({ from, via, to, outline }) => {
        document.querySelector('[data-modeling-help-annotation]')?.remove();
        const namespace = 'http://www.w3.org/2000/svg';
        const overlay = document.createElementNS(namespace, 'svg');
        overlay.dataset.modelingHelpAnnotation = '';
        overlay.setAttribute('viewBox', `0 0 ${window.innerWidth} ${window.innerHeight}`);
        Object.assign(overlay.style, {
            position: 'fixed',
            inset: '0',
            width: '100vw',
            height: '100vh',
            pointerEvents: 'none',
            zIndex: '2147483647',
        });

        const editorStyles = getComputedStyle(document.querySelector('.apollon-editor') ?? document.documentElement);
        const token = (name: string, fallback: string) => editorStyles.getPropertyValue(name).trim() || fallback;
        const primary = token('--apollon-primary', '#3e8acc');
        const surface = token('--apollon-surface', '#ffffff');
        const foreground = token('--apollon-foreground', '#12161f');

        if (outline) {
            const rectangle = document.createElementNS(namespace, 'rect');
            rectangle.setAttribute('x', `${outline.x}`);
            rectangle.setAttribute('y', `${outline.y}`);
            rectangle.setAttribute('width', `${outline.width}`);
            rectangle.setAttribute('height', `${outline.height}`);
            rectangle.setAttribute('rx', '4');
            rectangle.setAttribute('fill', primary);
            rectangle.setAttribute('fill-opacity', '0.06');
            rectangle.setAttribute('stroke', primary);
            rectangle.setAttribute('stroke-width', '1.75');
            rectangle.setAttribute('stroke-dasharray', '6 5');
            overlay.append(rectangle);
        }

        const addLine = (stroke: string, width: number) => {
            if (!from) return;
            const path = document.createElementNS(namespace, 'path');
            const points = [from, ...(via ?? []), to];
            path.setAttribute('d', points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' '));
            path.setAttribute('fill', 'none');
            path.setAttribute('stroke', stroke);
            path.setAttribute('stroke-width', `${width}`);
            path.setAttribute('stroke-linecap', 'round');
            path.setAttribute('stroke-linejoin', 'round');
            path.setAttribute('stroke-dasharray', '7 6');
            overlay.append(path);
        };
        addLine(surface, 5);
        addLine(primary, 2.25);

        if (from) {
            const origin = document.createElementNS(namespace, 'circle');
            origin.setAttribute('cx', `${from.x}`);
            origin.setAttribute('cy', `${from.y}`);
            origin.setAttribute('r', '3.5');
            origin.setAttribute('fill', primary);
            origin.setAttribute('stroke', surface);
            origin.setAttribute('stroke-width', '1.5');
            overlay.append(origin);
        }

        const target = document.createElementNS(namespace, 'circle');
        target.setAttribute('cx', `${to.x}`);
        target.setAttribute('cy', `${to.y}`);
        target.setAttribute('r', '7');
        target.setAttribute('fill', surface);
        target.setAttribute('fill-opacity', '0.92');
        target.setAttribute('stroke', primary);
        target.setAttribute('stroke-width', '2');
        overlay.append(target);

        const cursorScale = 0.72;
        const cursor = document.createElementNS(namespace, 'path');
        cursor.setAttribute('d', 'M2 1.5V18.5L6.5 14.4L11 23.5L14.4 21.8L10 13.1L16.3 12.4Z');
        cursor.setAttribute('transform', `translate(${to.x - 2 * cursorScale} ${to.y - 1.5 * cursorScale}) scale(${cursorScale})`);
        cursor.setAttribute('fill', surface);
        cursor.setAttribute('stroke', foreground);
        cursor.setAttribute('stroke-width', '1.8');
        cursor.setAttribute('stroke-linejoin', 'round');
        cursor.setAttribute('vector-effect', 'non-scaling-stroke');
        cursor.style.filter = 'drop-shadow(0 1px 1px rgb(0 0 0 / 0.28))';
        overlay.append(cursor);
        document.body.append(overlay);
    }, annotation);
};

const removeGestureAnnotation = (page: Page) => page.evaluate(() => document.querySelector('[data-modeling-help-annotation]')?.remove());

const editClass = async (page: Page, node: Locator, values: { name: string; attribute: string; method: string }): Promise<void> => {
    await node.dblclick();
    const popover = page.locator('.apollon-popover');
    await expect(popover).toBeVisible();
    await popover.locator('input[value="Class"]').fill(values.name);
    await popover.locator('input[value="+ attribute: Type"]').fill(values.attribute);
    const methodInput = popover.locator('input[value="+ method()"]');
    await methodInput.fill(values.method);
    await page.keyboard.press('Tab');
    await expect(node).toContainText(values.method.slice(2));
    await page.locator('.react-flow__pane').click({ position: { x: 20, y: 20 }, force: true });
    await expect(popover).toBeHidden();
};

const captureAround = async (
    page: Page,
    frame: Locator,
    subjects: readonly Locator[],
    outputDirectory: string,
    name: string,
    theme: Theme,
    padding: number,
    minimumSize: MinimumScreenshotSize,
    verticalOffset = 0,
): Promise<void> => {
    const frameBox = await requireBox(frame);
    const boxes = await Promise.all(subjects.map(requireBox));
    const subjectLeft = Math.min(...boxes.map((box) => box.x)) - padding;
    const subjectTop = Math.min(...boxes.map((box) => box.y)) - padding;
    const subjectRight = Math.max(...boxes.map((box) => box.x + box.width)) + padding;
    const subjectBottom = Math.max(...boxes.map((box) => box.y + box.height)) + padding;
    const width = Math.min(Math.max(subjectRight - subjectLeft, minimumSize.width), frameBox.width);
    const height = Math.min(Math.max(subjectBottom - subjectTop, minimumSize.height), frameBox.height);
    const left = Math.min(Math.max((subjectLeft + subjectRight - width) / 2, frameBox.x), frameBox.x + frameBox.width - width);
    const top = Math.min(Math.max((subjectTop + subjectBottom - height) / 2 + verticalOffset, frameBox.y), frameBox.y + frameBox.height - height);

    await page.screenshot({
        path: path.join(outputDirectory, `${name}-${theme}.png`),
        clip: { x: left, y: top, width, height },
        animations: 'disabled',
        caret: 'hide',
        style: '[data-apollon-region="bottom-center"], .modeling-editor__bottom-center { visibility: hidden !important; }',
    });
};

const dragPaletteClassTo = async (page: Page, paletteClass: Locator, position: { x: number; y: number }, release = true): Promise<Locator> => {
    const paletteBox = await requireBox(paletteClass);
    const source = center(paletteBox);
    await page.mouse.move(source.x, source.y);
    await page.mouse.down();
    await page.mouse.move(position.x, position.y, { steps: 15 });
    const preview = page.locator('body > *').last();
    await expect(preview).toBeVisible();
    if (release) {
        await page.mouse.up();
    }
    return preview;
};

const applyTheme = async (page: Page, theme: Theme): Promise<void> => {
    await page.evaluate((nextTheme) => localStorage.setItem('artemisapp.theme.preference', JSON.stringify(nextTheme.toUpperCase())), theme);
    await page.goto(page.url());
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    await expect(page.locator('.modeling-editor__frame')).toBeVisible();
};

const locateEditor = async (page: Page) => {
    const frame = page.locator('.modeling-editor__frame');
    await frame.scrollIntoViewIfNeeded();
    const canvas = frame.locator('.react-flow');
    const canvasBox = await requireBox(canvas);
    return {
        frame,
        paletteClass: page.getByRole('button', { name: 'Add element: Class' }),
        firstPosition: { x: canvasBox.x + canvasBox.width * 0.43, y: canvasBox.y + canvasBox.height * 0.43 },
        secondPosition: { x: canvasBox.x + canvasBox.width * 0.65, y: canvasBox.y + canvasBox.height * 0.43 },
        thirdPosition: { x: canvasBox.x + canvasBox.width * 0.83, y: canvasBox.y + canvasBox.height * 0.63 },
    };
};

/**
 * An asset generator, not coverage: it drives the live editor to re-shoot the walkthrough images in
 * `content/images/modeling-help` for both themes, and asserts nothing about the product. It is skipped
 * unless explicitly invoked, so it contributes no signal to a normal run.
 *
 * That the shipped images exist and resolve IS asserted, by the help dialog checks in
 * `ModelingEditorFullscreen.spec.ts`.
 */
test.describe('Modeling help assets', { tag: '@tool' }, () => {
    test.skip(!regenerateAssets, 'Regenerate with UPDATE_MODELING_HELP_SCREENSHOTS=1 pnpm exec playwright test tools/regenerate-modeling-help-assets.spec.ts --grep @tool.');
    test.use({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 });

    test('regenerates focused walkthrough images from the live editor', async ({ login, page }, testInfo) => {
        const outputDirectory = testInfo.outputPath('modeling-help');
        await fs.mkdir(outputDirectory, { recursive: true });
        await login(instructor, `/course-management/${courseId}/modeling-exercises/new`);
        await dismissPasskeyReminderIfPresent(page);

        for (const theme of themes) {
            await applyTheme(page, theme);

            let { frame, paletteClass, firstPosition } = await locateEditor(page);

            await dragPaletteClassTo(page, paletteClass, firstPosition);
            const singleNode = frame.locator('.react-flow__node').first();
            await expect(singleNode).toBeVisible();
            await editClass(page, singleNode, { name: 'Book', attribute: '+ title: String', method: '+ borrow(): void' });
            const paletteClassDiagramBox = await requireBox(paletteClass.locator('svg'));
            const createdNodeBox = await requireBox(singleNode);
            await addGestureAnnotation(page, {
                from: { x: paletteClassDiagramBox.x + paletteClassDiagramBox.width, y: paletteClassDiagramBox.y + paletteClassDiagramBox.height * 0.2 },
                to: { x: createdNodeBox.x + 20, y: createdNodeBox.y + 20 },
            });
            await captureAround(page, frame, [paletteClass, singleNode], outputDirectory, 'create-element', theme, 36, { width: 560, height: 230 }, 12);
            await removeGestureAnnotation(page);

            const sourceNodeBox = await requireBox(singleNode);
            const sourceDragPoint = { x: sourceNodeBox.x + 20, y: sourceNodeBox.y + 10 };
            await page.mouse.move(sourceDragPoint.x, sourceDragPoint.y);
            await page.mouse.down();
            await page.mouse.move(sourceDragPoint.x + 90, sourceDragPoint.y + 55, { steps: 12 });
            const movedNodeBox = await requireBox(singleNode);
            await addGestureAnnotation(page, { from: sourceDragPoint, to: { x: movedNodeBox.x + 20, y: movedNodeBox.y + 10 }, outline: sourceNodeBox });
            await captureAround(page, frame, [singleNode], outputDirectory, 'move-element', theme, 64, { width: 400, height: 280 });
            await removeGestureAnnotation(page);
            await page.mouse.up();

            await singleNode.click();
            const resizeHandle = singleNode.locator('.react-flow__resize-control.handle.bottom.right');
            const nodeBeforeResize = await requireBox(singleNode);
            const resizeHandleCenter = center(await requireBox(resizeHandle));
            await page.mouse.move(resizeHandleCenter.x, resizeHandleCenter.y);
            await page.mouse.down();
            await page.mouse.move(resizeHandleCenter.x + 75, resizeHandleCenter.y + 45, { steps: 12 });
            const nodeAfterResize = await requireBox(singleNode);
            await addGestureAnnotation(page, {
                from: { x: nodeBeforeResize.x + nodeBeforeResize.width, y: nodeBeforeResize.y + nodeBeforeResize.height },
                to: { x: nodeAfterResize.x + nodeAfterResize.width, y: nodeAfterResize.y + nodeAfterResize.height },
                outline: nodeBeforeResize,
            });
            await captureAround(page, frame, [singleNode], outputDirectory, 'resize-element', theme, 64, { width: 400, height: 250 });
            await removeGestureAnnotation(page);
            await page.mouse.up();

            await page.goto(page.url());
            await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
            const relationshipEditor = await locateEditor(page);
            ({ frame, paletteClass, firstPosition } = relationshipEditor);
            const { secondPosition, thirdPosition } = relationshipEditor;
            await dragPaletteClassTo(page, paletteClass, firstPosition);
            await dragPaletteClassTo(page, paletteClass, secondPosition);
            const nodes = frame.locator('.react-flow__node');
            await expect(nodes).toHaveCount(2);
            const cleanSourceNode = nodes.first();
            const targetNode = nodes.nth(1);
            await editClass(page, cleanSourceNode, { name: 'Book', attribute: '+ title: String', method: '+ borrow(): void' });
            await editClass(page, targetNode, { name: 'Author', attribute: '+ name: String', method: '+ write(): Book' });

            await cleanSourceNode.hover();
            const sourceHandle = cleanSourceNode.locator('.apollon-arc-handle--right').nth(1);
            const targetHandle = targetNode.locator('.apollon-arc-handle--left').nth(1);
            const sourceHandleCenter = center(await requireBox(sourceHandle));
            const targetHandleCenter = center(await requireBox(targetHandle));
            await page.mouse.move(sourceHandleCenter.x, sourceHandleCenter.y);
            await page.mouse.down();
            await page.mouse.move(targetHandleCenter.x, targetHandleCenter.y, { steps: 18 });
            await addGestureAnnotation(page, { from: sourceHandleCenter, to: targetHandleCenter });
            await captureAround(page, frame, [cleanSourceNode, targetNode], outputDirectory, 'create-relationship', theme, 58, { width: 560, height: 240 });
            await removeGestureAnnotation(page);
            await page.mouse.up();

            const edge = frame.locator('.react-flow__edge').first();
            await edge.dispatchEvent('click');
            const initialTargetHandleCenter = center(await requireBox(edge.locator('.edge-endpoint-handle--target')));
            await targetNode.hover();
            const authorBottomHandleCenter = center(await requireBox(targetNode.locator('.apollon-arc-handle--bottom').nth(1)));
            await page.mouse.move(initialTargetHandleCenter.x, initialTargetHandleCenter.y);
            await page.mouse.down();
            await page.mouse.move(authorBottomHandleCenter.x, authorBottomHandleCenter.y, { steps: 12 });
            await page.mouse.up();

            await cleanSourceNode.dblclick();
            const popover = page.locator('.apollon-popover');
            await expect(popover).toBeVisible();
            await captureAround(page, frame, [cleanSourceNode, popover], outputDirectory, 'update-element', theme, 32, { width: 540, height: 540 });
            await page.keyboard.press('Escape');

            await dragPaletteClassTo(page, paletteClass, thirdPosition);
            await expect(nodes).toHaveCount(3);
            const thirdNode = nodes.nth(2);
            await editClass(page, thirdNode, { name: 'Publisher', attribute: '+ name: String', method: '+ publish(): Book' });
            await edge.dispatchEvent('click');
            const reconnectHandle = edge.locator('.edge-endpoint-handle--target');
            const reconnectHandleCenter = center(await requireBox(reconnectHandle));
            await thirdNode.hover();
            const thirdNodeHandleCenter = center(await requireBox(thirdNode.locator('.apollon-arc-handle--left').nth(1)));
            await page.mouse.move(reconnectHandleCenter.x, reconnectHandleCenter.y);
            await page.mouse.down();
            await page.mouse.move(thirdNodeHandleCenter.x, thirdNodeHandleCenter.y, { steps: 18 });
            await page.mouse.up();
            await addGestureAnnotation(page, {
                from: reconnectHandleCenter,
                via: [{ x: reconnectHandleCenter.x, y: reconnectHandleCenter.y + 36 }],
                to: thirdNodeHandleCenter,
            });
            await captureAround(page, frame, [cleanSourceNode, targetNode, thirdNode], outputDirectory, 'reconnect-relationship', theme, 48, { width: 740, height: 340 });
            await removeGestureAnnotation(page);
        }

        await fs.mkdir(assetDirectory, { recursive: true });
        await Promise.all(
            assetNames.flatMap((name) => themes.map((theme) => fs.copyFile(path.join(outputDirectory, `${name}-${theme}.png`), path.join(assetDirectory, `${name}-${theme}.png`)))),
        );
    });
});
