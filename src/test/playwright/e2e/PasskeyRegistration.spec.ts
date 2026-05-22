import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { BASE_API } from '../support/constants';

const passkeyTestUser = { username: 'passkey_test_user', password: 'passkey_test_user' };

test.describe('Passkey registration', () => {
    test.beforeEach(async ({ page, login }) => {
        // Create a dedicated user for passkey tests (logged in as admin via API)
        await login(admin, '/courses');
        await page.request.post(`${BASE_API}/core/admin/users`, {
            data: {
                login: passkeyTestUser.username,
                password: passkeyTestUser.password,
                firstName: 'Passkey',
                lastName: 'TestUser',
                email: 'passkey_test_user@example.com',
                authorities: ['ROLE_USER'],
            },
            failOnStatusCode: false,
        });
    });

    test.afterEach(async ({ page, login }) => {
        // Clean up: delete the test user (as admin)
        await login(admin, '/courses');
        await page.request.delete(`${BASE_API}/core/admin/users/${passkeyTestUser.username}`);
    });

    test('registers a passkey via the setup modal after login', async ({ page, loginPage, virtualAuthenticator }) => {
        await page.goto('/sign-in');
        // Clear the passkey modal suppression so the modal appears for this test
        await page.evaluate(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'));
        await loginPage.login(passkeyTestUser);

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
