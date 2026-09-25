import { expect, test } from '@playwright/test';

test('links the published library and supplies forms, overlays, icons, typography and themes without a host reset', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto('/');
    const trigger = page.getByRole('button', { name: 'Open dialog' });
    await expect(trigger).toBeDisabled();
    await page.getByRole('checkbox', { name: 'Accept terms' }).check();
    await expect(trigger).toBeEnabled();
    const typography = await trigger.evaluate((button) => {
        const style = getComputedStyle(button);
        return { fontFamily: style.fontFamily, lineHeight: style.lineHeight };
    });
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Published package' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Close' })).toBeFocused();
    await expect(dialog.getByRole('button', { name: 'Close' }).locator('svg')).toBeVisible();
    const content = dialog.getByText('Rendered from the npm tarball.');
    await expect(content).toHaveCSS('font-family', typography.fontFamily);
    await expect(content).toHaveCSS('line-height', typography.lineHeight);
    await expect(dialog.getByRole('button', { name: 'Close' })).toHaveCSS('font-family', typography.fontFamily);
    await page.evaluate(() => document.documentElement.style.setProperty('--tumaet-ui-font-family', 'monospace'));
    await expect(content).toHaveCSS('font-family', 'monospace');
    await expect(dialog.getByRole('button', { name: 'Close' })).toHaveCSS('font-family', 'monospace');
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(trigger).toBeFocused();
    await page.getByRole('combobox', { name: 'Role' }).click();
    await expect(page.getByRole('option', { name: 'Student', exact: true })).toHaveCSS('font-family', 'monospace');
    await expect(page.getByRole('textbox')).toHaveCSS('font-family', 'monospace');
    await page.getByRole('option', { name: 'Instructor', exact: true }).click();
    await expect(page.getByRole('combobox', { name: 'Role' })).toHaveText('Instructor');
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'light');
    const lightBackground = await trigger.evaluate((button) => getComputedStyle(button).backgroundColor);
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'dark');
    await expect(trigger).not.toHaveCSS('background-color', lightBackground);
    await page.evaluate(() => document.documentElement.style.setProperty('--tumaet-ui-primary-color', 'rgb(12, 34, 56)'));
    await expect(trigger).toHaveCSS('background-color', 'rgb(12, 34, 56)');
    expect(errors).toEqual([]);
});

test('runs the menu and tabs on the Angular Aria peer the consumer installed', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.goto('/');

    const trigger = page.getByRole('button', { name: 'Course actions' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem', { name: 'Add students' })).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(page.getByText('Chosen: tutors')).toBeVisible();
    await expect(trigger).toBeFocused();

    const overview = page.getByRole('tab', { name: 'Overview' });
    await expect(overview).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel')).toHaveText('Overview panel');
    await overview.focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: 'Settings' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('tabpanel')).toHaveText('Settings panel');
    expect(errors).toEqual([]);
});
