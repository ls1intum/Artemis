import { expect, test } from '@playwright/test';

test('applies the compact viewport to responsive table columns', async ({ page }) => {
    await page.goto('/?path=/story/data-display-table--compact-viewport');

    const preview = page.locator('#storybook-preview-iframe');
    await expect(preview).toHaveCSS('width', '360px');

    const canvas = page.frameLocator('#storybook-preview-iframe');
    await expect(canvas.getByRole('columnheader', { name: 'Participant' })).toBeVisible();
    await expect(canvas.getByRole('columnheader', { name: 'Status' })).toBeHidden();
});
