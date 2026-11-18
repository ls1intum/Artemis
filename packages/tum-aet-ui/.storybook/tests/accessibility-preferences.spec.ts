import { expect, test } from '@playwright/test';

async function openStory(page: import('@playwright/test').Page, id: string) {
    await page.goto(`./iframe.html?id=${id}&viewMode=story`);
}

test('preserves native controls and distinguishable states in forced-colors mode', async ({ page }) => {
    await page.emulateMedia({ forcedColors: 'active' });

    for (const control of [
        ['forms-checkbox--default', '.tumaet-ui-checkbox-input', '.tumaet-ui-checkbox-box'],
        ['forms-radio-button--default', '.tumaet-ui-radio-button-input', '.tumaet-ui-radio-button-box'],
        ['forms-toggle-switch--default', '.tumaet-ui-toggle-switch-input', '.tumaet-ui-toggle-switch-handle'],
    ]) {
        await openStory(page, control[0]);
        await expect(page.locator(control[1]).first()).toHaveCSS('opacity', '1');
        await expect(page.locator(control[2]).first()).toHaveCSS('display', 'none');
    }

    await openStory(page, 'forms-select-button--default');
    const selected = page.getByRole('button', { pressed: true });
    const unselected = page.getByRole('button', { pressed: false }).first();
    await expect(selected).toBeVisible();
    expect(await selected.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(
        await unselected.evaluate((element) => getComputedStyle(element).backgroundColor),
    );

    await openStory(page, 'feedback-progress-bar--default');
    const progress = page.getByRole('progressbar');
    const value = progress.locator('.tumaet-ui-progress-bar-value');
    expect(await value.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(await progress.evaluate((element) => getComputedStyle(element).backgroundColor));
});
