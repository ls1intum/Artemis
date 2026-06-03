import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { BASE_API } from '../support/constants';

const passkeyTestUser = { username: 'passkey_test_user', password: 'passkey_test_user' };

test.describe('Passkey', () => {
    test.beforeEach(async ({ page, login }) => {
        await login(admin, '/courses');
        // Delete the user first to ensure clean state (removes any leftover passkeys from prior runs)
        await page.request.delete(`${BASE_API}/core/admin/users/${passkeyTestUser.username}`, { failOnStatusCode: false });
        await page.request.post(`${BASE_API}/core/admin/users`, {
            data: {
                login: passkeyTestUser.username,
                password: passkeyTestUser.password,
                firstName: 'Passkey',
                lastName: 'TestUser',
                email: 'passkey_test_user@example.com',
                authorities: ['ROLE_USER'],
            },
        });
    });

    test.afterEach(async ({ page }) => {
        await page.context().clearCookies();
        await page.request.post(`api/core/public/authenticate`, {
            data: { username: admin.username, password: admin.password, rememberMe: true },
        });
        await page.request.delete(`${BASE_API}/core/admin/users/${passkeyTestUser.username}`, { failOnStatusCode: false });
    });

    test('registers a passkey via the setup modal and displays it in user settings', async ({ page, loginPage, navigationBar, virtualAuthenticator }) => {
        // Clear the admin session from beforeEach so we land on the sign-in page
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await page.evaluate(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'));
        await loginPage.login(passkeyTestUser);

        // Register passkey via the modal
        await page.getByRole('button', { name: 'Set Up Passkey' }).click();

        const successAlert = page.locator('.alert-inner').getByText('Your passkey has been successfully registered.');
        await expect(successAlert).toBeVisible();
        await page.waitForURL('**/courses**');

        // Navigate to passkey settings and verify the passkey is listed
        await page.goto('/user-settings/passkeys');
        await expect(page.getByText('Your passkeys')).toBeVisible();
        await expect(page.getByText('passkey_test_user@example.com')).toBeVisible();
    });

    test('renames a passkey in user settings', async ({ page, login, virtualAuthenticator }) => {
        // Register passkey programmatically via API + virtual authenticator
        await registerPasskeyViaApi(page, passkeyTestUser);

        // Login and navigate to passkey settings
        await login(passkeyTestUser, '/user-settings/passkeys');
        await expect(page.getByText('Your passkeys')).toBeVisible();

        // Click Edit on the first passkey
        await page.getByRole('button', { name: 'Edit' }).first().click();
        const labelInput = page.getByRole('textbox');
        await labelInput.clear();
        await labelInput.fill('My Renamed Passkey');
        await page.getByRole('button', { name: 'Save' }).click();

        // Verify the renamed label is displayed
        await expect(page.getByText('My Renamed Passkey')).toBeVisible();
    });

    test('deletes a passkey in user settings', async ({ page, login, virtualAuthenticator }) => {
        await registerPasskeyViaApi(page, passkeyTestUser);

        await login(passkeyTestUser, '/user-settings/passkeys');
        await expect(page.getByText('Your passkeys')).toBeVisible();
        await expect(page.getByText('passkey_test_user@example.com').first()).toBeVisible();

        // Delete the passkey
        await page.getByRole('button', { name: 'Delete' }).first().click();
        await page.getByTestId('delete-dialog-confirm-button').click();

        // Verify passkey is removed
        await expect(page.getByText('You have not registered any passkeys with Artemis yet.')).toBeVisible();
    });

    test('logs in with a registered passkey', async ({ page, loginPage, virtualAuthenticator }) => {
        await registerPasskeyViaApi(page, passkeyTestUser);

        // Clear session and go to sign-in page
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await page.evaluate(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'));

        // Wait for the passkey login button to be stable before clicking
        await page.locator('#passkey-login-button').waitFor({ state: 'visible' });
        await page.locator('#passkey-login-button').click();

        // Verify login succeeded by checking navigation to courses
        await page.waitForURL('**/courses**');
    });

    test('cannot login with a passkey after it was deleted', async ({ page, login, loginPage, virtualAuthenticator }) => {
        await registerPasskeyViaApi(page, passkeyTestUser);

        // Delete the passkey via API
        await login(passkeyTestUser, '/courses');
        const passkeysResponse = await page.request.get(`${BASE_API}/core/passkey/user`);
        const passkeys = await passkeysResponse.json();
        for (const passkey of passkeys) {
            await page.request.delete(`${BASE_API}/core/passkey/${passkey.credentialId}`);
        }

        // Clear session and try to login with passkey
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await page.evaluate(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'));

        // Wait for the passkey login button to be stable before clicking
        await page.locator('#passkey-login-button').waitFor({ state: 'visible' });
        await page.locator('#passkey-login-button').click();

        // Verify login fails with an error
        const errorAlert = page.locator('.alert-inner').getByText('No passkey was found for Artemis.');
        await expect(errorAlert).toBeVisible();

        // Verify user is still on the sign-in page
        await expect(page).toHaveURL(/sign-in/);
    });
});

/**
 * Registers a passkey for a user by authenticating and calling the WebAuthn API directly
 * from the browser context, using the CDP virtual authenticator.
 * This avoids navigating through the UI modal.
 */
async function registerPasskeyViaApi(page: import('@playwright/test').Page, user: { username: string; password: string }) {
    // Authenticate as the user to get JWT cookie
    const authResponse = await page.request.post('api/core/public/authenticate', {
        data: { username: user.username, password: user.password, rememberMe: true },
    });
    expect(authResponse.status()).toBe(200);

    // Navigate to app so navigator.credentials and fetch are available on the correct origin
    await page.goto('/');
    await page.waitForLoadState('load');

    // Get account info for the credential label
    const accountResponse = await page.request.get(`${BASE_API}/core/public/account`);
    const account = await accountResponse.json();

    // Perform the full WebAuthn registration inside the browser context
    const result = await page.evaluate(
        async ({ userId, email }: { userId: number; email: string }) => {
            function base64urlToBuffer(base64url: string): ArrayBuffer {
                const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
                const padding = '='.repeat((4 - (base64.length % 4)) % 4);
                const binary = atob(base64 + padding);
                return Uint8Array.from(binary, (c) => c.charCodeAt(0)).buffer;
            }

            // 1. Get registration options from the server
            const optionsResponse = await fetch('/webauthn/register/options', {
                method: 'POST',
                credentials: 'same-origin',
            });
            if (!optionsResponse.ok) {
                const body = await optionsResponse.text().catch(() => '');
                return `options-failed: ${optionsResponse.status} ${body}`;
            }
            const options = await optionsResponse.json();

            // 2. Create credential via the virtual authenticator
            const credential = (await navigator.credentials.create({
                publicKey: {
                    ...options,
                    challenge: base64urlToBuffer(options.challenge),
                    user: {
                        id: new TextEncoder().encode(userId.toString()),
                        name: email,
                        displayName: email,
                    },
                    excludeCredentials: options.excludeCredentials?.map((c: { id: string; type: string }) => ({
                        ...c,
                        id: base64urlToBuffer(c.id),
                    })),
                },
            })) as PublicKeyCredential | null;

            if (!credential) {
                return 'credential-null';
            }

            // 3. Register the credential with the server
            const registerResponse = await fetch('/webauthn/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({
                    publicKey: {
                        credential: credential.toJSON(),
                        label: `${email} - E2E Test`,
                    },
                }),
            });

            if (!registerResponse.ok) {
                const body = await registerResponse.text().catch(() => '');
                return `register-failed: ${registerResponse.status} ${body}`;
            }
            return 'success';
        },
        { userId: account.id, email: account.email },
    );

    expect(result).toBe('success');
}
