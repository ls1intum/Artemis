import { expect, type Page } from '@playwright/test';

import { instructor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const courseId = SEED_COURSES.exerciseManagement.id;

const enterFullscreen = async (page: Page) => {
    await page.getByTestId('modeling-editor-fullscreen').click();
    await expect.poll(() => page.evaluate(() => document.fullscreenElement === document.documentElement)).toBe(true);
    return page.locator('.modeling-editor__frame--fullscreen');
};

test.describe('Fullscreen modeling editor', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 1440, height: 1000 } });

    test('keeps the problem statement and explanation usable in fullscreen', async ({ login, page }) => {
        await login(instructor, `/course-management/${courseId}/modeling-exercises/new`);
        await expect(page.getByTestId('apollon-palette').first()).toBeVisible();

        const problemStatementEditor = page.locator('#field_problemStatement .monaco-editor');
        await problemStatementEditor.click();
        await page.keyboard.insertText('## Design task\n\nModel a library with books and authors.');

        const frame = await enterFullscreen(page);
        const problemStatement = frame.getByTestId('modeling-editor-problem-statement');
        const panel = frame.locator('.apollon-rail-disclosure__panel');
        const explanation = frame.locator('.modeling-exercise-example-explanation');
        await expect(problemStatement).toHaveAttribute('aria-expanded', 'true');
        await expect(panel).toContainText('Design task');
        await expect(explanation).toBeVisible();

        const resizeHandle = frame.locator('.apollon-rail-disclosure__resizer--left');
        await expect(resizeHandle).toHaveAttribute('role', 'separator');
        await expect(resizeHandle).toHaveAttribute('aria-orientation', 'vertical');
        const panelWidth = (await panel.boundingBox())!.width;
        const resizeHandleBox = await resizeHandle.boundingBox();
        expect(resizeHandleBox).not.toBeNull();
        await page.mouse.move(resizeHandleBox!.x + resizeHandleBox!.width / 2, resizeHandleBox!.y + resizeHandleBox!.height / 2);
        await page.mouse.down();
        await page.mouse.move(resizeHandleBox!.x - 80, resizeHandleBox!.y, { steps: 12 });
        await page.mouse.up();
        await expect.poll(async () => (await panel.boundingBox())!.width).toBeGreaterThan(panelWidth + 60);

        await page.getByRole('button', { name: 'Exit Fullscreen' }).click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect(page.locator('.modeling-editor__frame--fullscreen')).toHaveCount(0);
        await expect(page.locator('.modeling-editor__frame')).toBeVisible();
    });

    test('keeps portaled editor interactions visible in fullscreen', async ({ login, page }) => {
        await login(instructor, `/course-management/${courseId}/modeling-exercises/new`);
        const frame = await enterFullscreen(page);

        const paletteClass = page.getByRole('button', { name: 'Add element: Class' });
        const canvas = frame.locator('.react-flow');
        const paletteBox = await paletteClass.boundingBox();
        const canvasBox = await canvas.boundingBox();
        expect(paletteBox).not.toBeNull();
        expect(canvasBox).not.toBeNull();

        const bodyChildren = page.locator('body > *');
        const bodyChildCount = await bodyChildren.count();
        await page.mouse.move(paletteBox!.x + paletteBox!.width / 2, paletteBox!.y + paletteBox!.height / 2);
        await page.mouse.down();
        try {
            await page.mouse.move(canvasBox!.x + canvasBox!.width / 2, canvasBox!.y + canvasBox!.height / 2, { steps: 15 });
            await expect(bodyChildren).toHaveCount(bodyChildCount + 1);
        } finally {
            await page.mouse.up();
        }

        const node = frame.locator('.react-flow__node').first();
        await expect(node).toBeVisible();
        await node.click();
        await page.getByRole('button', { name: 'Edit element' }).click();

        const popover = page.locator('.apollon-popover');
        await expect(popover).toBeVisible();
        const name = popover.getByRole('textbox', { name: 'Name', exact: true }).first();
        await name.fill('Fullscreen class');
        await expect(name).toHaveValue('Fullscreen class');
        await page.keyboard.press('Escape');
        await expect(popover).toBeHidden();

        await page.getByRole('button', { name: 'Help' }).click();
        const helpDialog = page.getByRole('dialog');
        await expect(helpDialog).toBeVisible();
        await expect(helpDialog.getByRole('tab', { name: 'Walkthrough' })).toBeVisible();
        await helpDialog.getByRole('button', { name: 'Close' }).click();

        await page.getByRole('button', { name: 'Exit Fullscreen' }).click();
        await expect.poll(() => page.evaluate(() => document.fullscreenElement)).toBeNull();
        await expect(page.locator('.modeling-editor__frame--fullscreen')).toHaveCount(0);
    });
});
