import { expect } from '@playwright/test';
import { test } from '../support/fixtures';

test('Passkey reminder modal is not displayed on re-login after do not remind for 30 days was chosen', async ({ page, loginPage, navigationBar }) => {
    const artemisAdmin = { username: 'artemis_admin', password: 'artemis_admin' };

    // First login
    await page.goto('/');
    await loginPage.login(artemisAdmin);

    // Hide Modal for 30 days
    await expect(page.getByRole('button', { name: 'Remind Me in 30 Days' })).toBeVisible();
    await page.getByRole('button', { name: 'Remind Me in 30 Days' }).click();

    // Logout
    await navigationBar.logout();

    // Second login
    await loginPage.login(artemisAdmin);

    // Verify the reminder button is not shown
    await expect(page.getByRole('button', { name: 'Remind Me in 30 Days' })).not.toBeVisible();
});
