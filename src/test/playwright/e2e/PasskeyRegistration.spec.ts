import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { BASE_API } from '../support/constants';

test.describe('Passkey registration', () => {
    test.afterEach(async ({ page }) => {
        // Clean up any passkeys registered during the test to keep tests idempotent.
        // This ensures the passkey setup modal still appears on the next run.
        const response = await page.request.get(`${BASE_API}/core/passkey/user`);
        if (response.ok()) {
            const passkeys = await response.json();
            for (const passkey of passkeys) {
                await page.request.delete(`${BASE_API}/core/passkey/${passkey.credentialId}`);
            }
        }
    });

    test('registers a passkey via the setup modal after login', async ({ page, loginPage, virtualAuthenticator }) => {
        await page.goto('/sign-in');
        await loginPage.login(admin);

        // The passkey setup modal appears after login for users without a passkey
        await page.getByRole('button', { name: 'Set Up Passkey' }).click();

        // Verify no registration error alert is shown
        const errorAlert = page.locator('.alert-inner').getByText('The passkey could not be registered. Please try again.');
        await expect(errorAlert).not.toBeVisible();

        // Verify success alert is shown
        const successAlert = page.locator('.alert-inner').getByText('Your passkey has been successfully registered. You can manage your passkeys in the user settings');
        await expect(successAlert).toBeVisible();

        // Modal should close and navigate to courses
        await page.waitForURL('**/courses**');
    });
});
