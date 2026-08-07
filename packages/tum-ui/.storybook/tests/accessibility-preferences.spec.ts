import { expect, test } from '@playwright/test';

async function openStory(page: import('@playwright/test').Page, id: string) {
    await page.goto(`./iframe.html?id=${id}&viewMode=story`);
}

test('preserves native controls and distinguishable states in forced-colors mode', async ({ page }) => {
    await page.emulateMedia({ forcedColors: 'active' });

    for (const control of [
        ['forms-checkbox--default', '.tum-ui-checkbox-input', '.tum-ui-checkbox-box'],
        ['forms-radio-button--default', '.tum-ui-radio-button-input', '.tum-ui-radio-button-box'],
        ['forms-toggle-switch--default', '.tum-ui-toggle-switch-input', '.tum-ui-toggle-switch-handle'],
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
    const value = progress.locator('.tum-ui-progress-bar-value');
    expect(await value.evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(await progress.evaluate((element) => getComputedStyle(element).backgroundColor));
});
