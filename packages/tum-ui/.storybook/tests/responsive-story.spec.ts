import { expect, test } from '@playwright/test';

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

    expect(await radii()).toEqual([
        ['6px', '0px'],
        ['0px', '0px'],
        ['0px', '6px'],
    ]);
    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    await expect(addons.first()).toBeVisible();
    await expect(input).toBeVisible();
    expect(await radii()).toEqual([
        ['0px', '6px'],
        ['0px', '0px'],
        ['6px', '0px'],
    ]);

    await page.goto('./iframe.html?id=forms-select-button--default&viewMode=story');
    await page.locator('html').evaluate((element) => element.setAttribute('dir', 'rtl'));
    const options = page.getByRole('button');
    await expect(options).toHaveCount(3);
    expect(await options.first().evaluate((element) => getComputedStyle(element).borderRightWidth)).toBe('1px');
    expect(await options.last().evaluate((element) => getComputedStyle(element).borderLeftWidth)).toBe('1px');
});
