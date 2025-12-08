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

    // Wait for navigation after clicking the button (modal should close and navigate)
    await page.waitForURL('**/courses**');

    // Logout
    await navigationBar.logout();

    // Second login
    await loginPage.login(artemisAdmin);

    // Race between modal appearing and navigation completing
    // If working correctly: navigation wins (modal never appears)
    // If bug exists: modal appears before navigation
    const modalButton = page.getByRole('button', { name: 'Remind Me in 30 Days' });

    const result = await Promise.race([modalButton.waitFor({ state: 'visible' }).then(() => 'modal-appeared'), page.waitForURL('**/courses**').then(() => 'navigation-completed')]);

    // If the modal appeared first, the test should fail
    if (result === 'modal-appeared') {
        throw new Error('Passkey reminder modal appeared on second login, but it should not have!');
    }

    // Verify the modal is not visible after navigation
    await expect(modalButton).not.toBeVisible();
});
