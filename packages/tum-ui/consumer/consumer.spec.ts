import { expect, test } from '@playwright/test';

test('links the published library and supplies forms, overlays, icons, focus and themes without host styles', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto('/');
    const trigger = page.getByRole('button', { name: 'Open dialog' });
    await expect(trigger).toBeDisabled();
    await page.getByRole('checkbox', { name: 'Accept terms' }).check();
    await expect(trigger).toBeEnabled();
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Published package' });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText('Rendered from the npm tarball.');
    await expect(dialog.getByRole('button', { name: 'Close' })).toBeFocused();
    await expect(dialog.getByRole('button', { name: 'Close' }).locator('svg')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'light');
    const lightBackground = await trigger.evaluate((button) => getComputedStyle(button).backgroundColor);
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'dark');
    await expect(trigger).not.toHaveCSS('background-color', lightBackground);
    await page.evaluate(() => document.documentElement.style.setProperty('--tumaet-ui-primary-color', 'rgb(12, 34, 56)'));
    await expect(trigger).toHaveCSS('background-color', 'rgb(12, 34, 56)');
    expect(errors).toEqual([]);
});
