import { expect, test } from '@playwright/test';

test('connects the Artemis client guide and TUM UI component reference in both directions', async ({ page }) => {
    await page.goto('./developer/intro');

    await page.getByRole('main').getByRole('link', { name: 'Artemis client' }).click();
    await expect(page).toHaveURL(/\/developer\/artemis-client$/);
    await expect(page.getByRole('complementary').getByRole('link', { name: 'TUM UI component reference' })).toBeVisible();

    await page
        .getByRole('main')
        .getByRole('link', { name: /TUM UI components/ })
        .click();
    await expect(page).toHaveURL(/\/developer\/tum-ui\/\?path=\/docs\/actions-button--docs$/);
    await expect(page.getByRole('link', { name: 'Back to Artemis client guide' })).toBeVisible();

    const preview = page.frameLocator('#storybook-preview-iframe');
    await expect(preview.getByRole('heading', { level: 1, name: 'Button' })).toBeVisible();

    await page.getByRole('link', { name: 'Back to Artemis client guide' }).click();
    await expect(page).toHaveURL(/\/developer\/artemis-client$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Artemis client guide' })).toBeVisible();
});
