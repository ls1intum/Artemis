import { test, expect } from '@playwright/test';

test('Passkey reminder modal is not displayed on re-login after do not remind for 30 days was chosen', async ({ page }) => {
    await page.goto('http://localhost:9000/');
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').click();
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').fill('artemis_admin');
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').press('Tab');
    await page.getByLabel('Password').fill('artemis');
    await page.getByLabel('Password').press('ControlOrMeta+a');
    await page.getByLabel('Password').fill('artemis_admin');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('button', { name: 'Remind Me in 30 Days' }).click();
    await page.locator('#account-menu').click();
    await page.locator('#logout').click();
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').click();
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').fill('artemis_admin');
    await page.getByPlaceholder('e.g. go42tum / example@tum.de').press('Tab');
    await page.getByLabel('Password').fill('artemis_admin');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Remind Me in 30 Days' })).not.toBeVisible();
});
