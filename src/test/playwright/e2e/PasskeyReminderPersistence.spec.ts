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

    // The modal opens BEFORE navigation, so we need to check if it appears
    // before the page navigates away. We'll use a race condition:
    // Either the modal appears (test should fail) OR navigation happens (test should pass)
    const modalButton = page.getByRole('button', { name: 'Remind Me in 30 Days' });

    try {
        // Try to wait for the button to appear with a short timeout
        // If it appears, the test should fail
        await modalButton.waitFor({ state: 'visible', timeout: 2000 });

        // If we get here, the button appeared - test should fail
        throw new Error('Passkey reminder modal appeared on second login, but it should not have!');
    } catch (error: any) {
        // If the timeout occurs (button never appeared), that's expected - test passes
        if (error.message?.includes('Timeout')) {
            // Good! The button didn't appear within 2 seconds
            console.log('✓ Modal did not appear (expected behavior)');
        } else {
            // Some other error - re-throw it
            throw error;
        }
    }

    // Additional check: After navigation, the button should definitely not be visible
    await page.waitForURL('**/courses**');
    await expect(modalButton).not.toBeVisible();
});
