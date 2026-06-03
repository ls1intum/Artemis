import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { BASE_API } from '../support/constants';

const TEST_USER = { username: 'passkey_reminder_e2e', password: 'passkey_reminder_e2e', email: 'passkey_reminder_e2e@example.com' };

test.beforeEach(async ({ page }) => {
    await page.request.post('api/core/public/authenticate', {
        data: { username: admin.username, password: admin.password, rememberMe: true },
    });
    await page.request.delete(`${BASE_API}/core/admin/users/${TEST_USER.username}`, { failOnStatusCode: false });
    await page.request.post(`${BASE_API}/core/admin/users`, {
        data: {
            login: TEST_USER.username,
            password: TEST_USER.password,
            firstName: 'Passkey',
            lastName: 'ReminderTest',
            email: TEST_USER.email,
            authorities: ['ROLE_USER'],
        },
    });
});

test.afterEach(async ({ page }) => {
    await page.context().clearCookies();
    await page.request.post('api/core/public/authenticate', {
        data: { username: admin.username, password: admin.password, rememberMe: true },
    });
    await page.request.delete(`${BASE_API}/core/admin/users/${TEST_USER.username}`, { failOnStatusCode: false });
});

test('Passkey reminder modal is not displayed on re-login after remind me in 30 days was chosen', async ({ page, loginPage, navigationBar }) => {
    // Ensure the passkey setup modal is shown on every page load.
    // The autoTestFixture init script suppresses the modal globally; this overrides it by removing
    // the suppression key after that script runs (init scripts execute in registration order).
    // Logout in Artemis is SPA navigation (no page reload), so this init script fires only once
    // during page.goto('/sign-in'), and the key set by "Remind me in 30 days" persists afterward.
    await page.addInitScript(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'));

    // First login — clear the admin session from beforeEach
    await page.context().clearCookies();
    await page.goto('/sign-in');
    await loginPage.login(TEST_USER);

    // Modal should appear since the test user has no passkeys
    await expect(page.getByRole('button', { name: 'Remind Me in 30 Days' })).toBeVisible();
    await page.getByRole('button', { name: 'Remind Me in 30 Days' }).click();

    // Wait for navigation after dismissing the modal
    await page.waitForURL('**/courses**');

    // Logout — SPA navigation, localStorage key set by "Remind me in 30 days" persists
    await navigationBar.logout();

    // Second login — the 30-day suppression stored in localStorage should prevent the modal
    await loginPage.login(TEST_USER);
    await page.waitForURL('**/courses**');

    // Modal must not appear on re-login
    await expect(page.getByRole('button', { name: 'Remind Me in 30 Days' })).not.toBeVisible();
});
