import { expect, test } from '@playwright/test';
import type { Locator } from '@playwright/test';

function observeTransition(element: Locator): Promise<boolean> {
    return element.evaluate(
        (node) =>
            new Promise<boolean>((resolve) => {
                node.addEventListener('transitionrun', () => resolve(true), { once: true });
                setTimeout(() => resolve(false), 500);
            }),
    );
}

test('applies the compact viewport to responsive table columns', async ({ page }) => {
    await page.goto('./?path=/story/data-display-table--compact-viewport');

    const preview = page.locator('#storybook-preview-iframe');
    const canvas = page.frameLocator('#storybook-preview-iframe');
    await expect(preview).toBeAttached();
    await expect(canvas.getByRole('columnheader', { name: 'Participant' })).toBeVisible();
    await expect(preview).toHaveCSS('width', '360px');
    await expect(canvas.getByRole('columnheader', { name: 'Status' })).toBeHidden();
});

test('contains wide tables without creating page-level horizontal scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });

    await page.goto('./iframe.html?id=data-display-table--row-actions&viewMode=story');
    await expect(page.getByRole('table')).toBeVisible();
    const tableWrapper = page.getByRole('table').locator('..');
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => document.documentElement.clientWidth));
    expect(await tableWrapper.evaluate((element) => element.scrollWidth)).toBeGreaterThan(await tableWrapper.evaluate((element) => element.clientWidth));

    await page.goto('./iframe.html?id=data-display-virtual-scroll-table--default&viewMode=story');
    const virtualTable = page.getByRole('table');
    await expect(virtualTable).toBeVisible();
    const participantHeader = page.getByRole('columnheader', { name: 'Participant' });
    const participantCell = page.getByRole('cell', { name: 'Participant 1' });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => document.documentElement.clientWidth));
    expect(await virtualTable.evaluate((element) => element.parentElement!.scrollWidth)).toBeGreaterThan(
        await virtualTable.evaluate((element) => element.parentElement!.clientWidth),
    );
    await virtualTable.evaluate((element) => {
        element.parentElement!.scrollLeft = 100;
    });
    await expect.poll(async () => (await participantHeader.boundingBox())?.x).toBe((await participantCell.boundingBox())?.x);
});

test('keeps tab navigation inside a compact viewport and reveals the focused tab', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 640 });
    await page.goto('./iframe.html?id=navigation-tabs--default&viewMode=story');

    const tabList = page.getByRole('tablist');
    const settings = page.getByRole('tab', { name: 'Settings' });
    await expect(tabList).toHaveCSS('overflow-x', 'auto');
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => document.documentElement.clientWidth));
    expect(await tabList.evaluate((element) => element.scrollWidth)).toBeGreaterThan(await tabList.evaluate((element) => element.clientWidth));

    await page.getByRole('tab', { name: 'Overview' }).focus();
    const initialScrollPosition = await tabList.evaluate((element) => element.scrollLeft);
    await page.keyboard.press('End');
    await expect(settings).toBeFocused();
    await expect(settings).toBeInViewport();
    await expect.poll(async () => tabList.evaluate((element) => element.scrollLeft)).toBeGreaterThan(initialScrollPosition);
});

test('aligns select-like overlays to their trigger', async ({ page }) => {
    for (const story of [
        { id: 'forms-autocomplete--default', name: 'Assignee', origin: '.tum-ui-autocomplete-container', panel: '.tum-ui-autocomplete-panel' },
        { id: 'forms-select--default', name: 'Course language', origin: '.tum-ui-select-trigger', panel: '.tum-ui-select-panel' },
    ]) {
        await page.setViewportSize({ width: 900, height: 700 });
        await page.goto(`./iframe.html?id=${story.id}&viewMode=story`);
        const trigger = page.getByRole('combobox', { name: story.name });
        await trigger.click();
        const origin = page.locator(story.origin);
        const panel = page.locator(story.panel);
        await expect(panel).toBeVisible();
        await expect.poll(async () => (await panel.boundingBox())?.width).toBe((await origin.boundingBox())?.width);

        await page.setViewportSize({ width: 280, height: 700 });
        await expect.poll(async () => (await panel.boundingBox())?.width).toBe((await origin.boundingBox())?.width);
    }
});

test('keeps select text and controls separate at compact widths in both directions', async ({ page }) => {
    await page.setViewportSize({ width: 280, height: 640 });
    await page.goto('./iframe.html?id=forms-select--selected&viewMode=story');

    const root = page.locator('tum-ui-select');
    const trigger = root.getByRole('combobox', { name: 'Course language' });
    const label = trigger.locator('span');
    const clear = root.getByRole('button', { name: 'Clear selection' });
    const indicator = root.locator(':scope > div > span').last();

    const overlap = async (first: Locator, second: Locator) => {
        const firstBox = (await first.boundingBox())!;
        const secondBox = (await second.boundingBox())!;
        return Math.max(0, Math.min(firstBox.x + firstBox.width, secondBox.x + secondBox.width) - Math.max(firstBox.x, secondBox.x));
    };

    await expect(clear).toBeVisible();
    expect(await overlap(label, clear)).toBe(0);
    expect(await overlap(clear, indicator)).toBe(0);

    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    expect(await overlap(label, clear)).toBe(0);
    expect(await overlap(clear, indicator)).toBe(0);
});

test('keeps selected dates visually selected on hover', async ({ page }) => {
    await page.goto('./iframe.html?id=forms-date-picker--default&viewMode=story');
    await page.getByRole('button', { name: 'Open calendar' }).click();
    const selectedDay = page.getByRole('gridcell', { selected: true }).locator('button');
    const selectedBackground = await selectedDay.evaluate((element) => getComputedStyle(element).backgroundColor);

    await selectedDay.hover();
    await expect(selectedDay).toHaveCSS('background-color', selectedBackground);
});

test('centers the native time control and commits its normalized value', async ({ page }) => {
    await page.goto('./iframe.html?id=forms-date-picker--default&viewMode=story');
    await page.getByRole('button', { name: 'Open calendar' }).click();
    const dialog = page.getByRole('dialog');
    const time = dialog.locator('input[type="time"]');
    const dialogBox = (await dialog.boundingBox())!;
    const timeBox = (await time.boundingBox())!;

    expect(timeBox.x + timeBox.width / 2).toBeCloseTo(dialogBox.x + dialogBox.width / 2, 0);
    await expect(time).toHaveAttribute('type', 'time');
    await expect(time).toHaveAttribute('step', '60');
    await time.fill('12:34');
    await expect(page.getByRole('combobox', { name: 'Deadline' })).toHaveValue('13.06.2026 12:34');
});

test('renders the initial radio selection in AutoDocs', async ({ page }) => {
    await page.goto('./iframe.html?id=forms-radio-button--docs&viewMode=docs');
    const checked = page.getByRole('radio', { name: 'Weekly' }).first();
    const box = checked.locator('xpath=following-sibling::*[1]');
    const uncheckedBox = page.getByRole('radio', { name: 'Daily' }).first().locator('xpath=following-sibling::*[1]');
    const marker = box.locator('.tum-ui-radio-button-icon');

    await expect(checked).toBeChecked();
    expect(await box.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(
        await uncheckedBox.evaluate((element) => getComputedStyle(element).backgroundColor),
    );
    await expect(marker).toHaveCSS('visibility', 'visible');
});

test('preserves component transitions in AutoDocs', async ({ page }) => {
    await page.goto('./iframe.html?id=forms-toggle-switch--docs&viewMode=docs');
    const toggle = page.getByRole('switch').first();
    const handle = page.locator('.tum-ui-toggle-switch-handle').first();

    const toggleTransition = observeTransition(handle);
    await toggle.click();
    expect(await toggleTransition).toBe(true);
    await expect(toggle).toBeChecked();

    await page.goto('./iframe.html?id=navigation-tabs--docs&viewMode=docs');
    const indicator = page.locator('.tum-ui-tab-indicator').first();
    const settings = page.getByRole('tab', { name: 'Settings' }).first();

    const tabTransition = observeTransition(indicator);
    await settings.click();
    expect(await tabTransition).toBe(true);
    await expect.poll(async () => Math.round((await indicator.boundingBox())?.width ?? 0)).toBe(Math.round((await settings.boundingBox())?.width ?? 0));
    await expect.poll(async () => Math.round((await indicator.boundingBox())?.x ?? 0)).toBe(Math.round((await settings.boundingBox())?.x ?? 0));
});

test('keeps input-group seams logical in both text directions', async ({ page }) => {
    await page.goto('./iframe.html?id=forms-input-group--default&viewMode=story');
    const addons = page.locator('tum-ui-input-group-addon');
    const input = page.getByRole('textbox', { name: 'Budget' });

    const radii = async () =>
        page.locator('tum-ui-input-group').evaluate((group) =>
            Array.from(group.children).map((element) => {
                const style = getComputedStyle(element);
                return [style.borderTopLeftRadius, style.borderTopRightRadius];
            }),
        );

    const ltrRadii = await radii();
    expect(ltrRadii[0][0]).not.toBe('0px');
    expect(ltrRadii[0][1]).toBe('0px');
    expect(ltrRadii[1]).toEqual(['0px', '0px']);
    expect(ltrRadii[2][0]).toBe('0px');
    expect(ltrRadii[2][1]).toBe(ltrRadii[0][0]);
    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    await expect(addons.first()).toBeVisible();
    await expect(input).toBeVisible();
    expect(await radii()).toEqual([[ltrRadii[0][1], ltrRadii[0][0]], ltrRadii[1], [ltrRadii[2][1], ltrRadii[2][0]]]);

    await page.goto('./iframe.html?id=forms-input-number--in-input-group&viewMode=story');
    const numberGroup = page.locator('tum-ui-input-group');
    const numberInput = page.getByRole('spinbutton', { name: 'Capacity' });
    const increment = page.locator('.tum-ui-input-number-increment');
    const decrement = page.locator('.tum-ui-input-number-decrement');
    const numberRadii = async () => numberInput.evaluate((element) => [getComputedStyle(element).borderTopLeftRadius, getComputedStyle(element).borderTopRightRadius]);
    const ltrNumberRadii = await numberRadii();
    expect(ltrNumberRadii[0]).toBe('0px');
    expect(ltrNumberRadii[1]).toBe('0px');
    expect(await numberGroup.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
    await numberInput.focus();
    await increment.click();
    await expect(numberInput).toBeFocused();
    await expect(numberInput).toHaveValue('1');
    await decrement.click();
    await expect(numberInput).toBeFocused();
    await expect(numberInput).toHaveValue('0');
    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    await expect(numberGroup).toBeVisible();
    expect(await numberRadii()).toEqual([ltrNumberRadii[1], ltrNumberRadii[0]]);

    await page.goto('./iframe.html?id=forms-select-button--default&viewMode=story');
    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    const options = page.getByRole('button');
    await expect(options).toHaveCount(3);
    expect(await options.first().evaluate((element) => getComputedStyle(element).borderRightWidth)).toBe('1px');
    expect(await options.last().evaluate((element) => getComputedStyle(element).borderLeftWidth)).toBe('1px');
});
