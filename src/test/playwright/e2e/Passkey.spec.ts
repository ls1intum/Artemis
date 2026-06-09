import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { BASE_API } from '../support/constants';

/**
 * JSON-serialized WebAuthn credential descriptor, as returned by the server.
 * See https://w3c.github.io/webauthn/#dictdef-publickeycredentialdescriptorjson
 * Type definitions based on @simplewebauthn/types (https://jsr.io/@simplewebauthn/types).
 */
interface PublicKeyCredentialDescriptorJSON {
    id: string;
    type: string;
    transports?: string[];
}

/**
 * JSON-serialized PublicKeyCredentialCreationOptions, as returned by the server's
 * /webauthn/register/options endpoint.
 * See https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptionsjson
 */
interface PublicKeyCredentialCreationOptionsJSON {
    challenge: string;
    excludeCredentials?: PublicKeyCredentialDescriptorJSON[];
    [key: string]: unknown;
}

function passkeyTestUser(testTitle: string) {
    const id = testTitle.split(' ')[0].toLowerCase();
    return { username: `passkey_e2e_${id}`, password: `passkey_e2e_${id}`, email: `passkey_e2e_${id}@example.com` };
}

/**
 * Disables WebAuthn conditional mediation so it cannot auto-complete login before an
 * explicit button click — with automaticPresenceSimulation:true the virtual authenticator
 * would otherwise resolve the background credentials.get() immediately.
 */
async function disableConditionalMediation(page: import('@playwright/test').Page) {
    await page.addInitScript(() => {
        if (window.PublicKeyCredential) {
            (window.PublicKeyCredential as unknown as Record<string, unknown>).isConditionalMediationAvailable = async () => false;
        }
    });
}

test.describe('Passkey', () => {
    test.beforeEach(async ({ page, login }, testInfo) => {
        const user = passkeyTestUser(testInfo.title);
        await login(admin, '/courses');
        // Delete the user first to ensure clean state (removes any leftover passkeys from prior runs)
        await page.request.delete(`${BASE_API}/core/admin/users/${user.username}`, { failOnStatusCode: false });
        await page.request.post(`${BASE_API}/core/admin/users`, {
            data: {
                login: user.username,
                password: user.password,
                firstName: 'Passkey',
                lastName: 'TestUser',
                email: user.email,
                authorities: ['ROLE_USER'],
            },
        });
    });

    test.afterEach(async ({ page }, testInfo) => {
        const user = passkeyTestUser(testInfo.title);
        await page.context().clearCookies();
        await page.request.post(`${BASE_API}/core/public/authenticate`, {
            data: { username: admin.username, password: admin.password, rememberMe: true },
        });
        await page.request.delete(`${BASE_API}/core/admin/users/${user.username}`, { failOnStatusCode: false });
    });

    test('registers a passkey via the setup modal and displays it in user settings', async ({ page, loginPage, virtualAuthenticator }) => {
        const user = passkeyTestUser(test.info().title);
        // Ensure the passkey setup modal is shown on every navigation, including the post-login redirect.
        // The autoTestFixture init script suppresses the modal globally via localStorage AND CSS;
        // this overrides both by removing the suppression key and re-enabling the dialog.
        await page.addInitScript(() => {
            localStorage.removeItem('earliestSetupPasskeyReminderDate');
            const style = document.createElement('style');
            style.textContent = '.p-dialog-mask:has(.passkey-setup-dialog) { display: flex !important; }';
            document.head.appendChild(style);
        });
        // Clear the admin session from beforeEach so we land on the sign-in page
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await loginPage.login(user);

        // Register passkey via the modal
        await page.getByRole('button', { name: 'Set Up Passkey' }).click();

        const successAlert = page.locator('.alert-inner').getByText('Your passkey has been successfully registered.');
        await expect(successAlert).toBeVisible();
        await page.waitForURL('**/courses**');

        // Navigate to passkey settings and verify the passkey is listed
        await page.goto('/user-settings/passkeys');
        await expect(page.getByText('Your passkeys')).toBeVisible();
        await expect(page.getByText(user.email)).toBeVisible();
    });

    test('renames a passkey in user settings', async ({ page, login, virtualAuthenticator }) => {
        const user = passkeyTestUser(test.info().title);
        // Register passkey programmatically via API + virtual authenticator
        await registerPasskeyViaApi(page, user);

        // Login and navigate to passkey settings
        await login(user, '/user-settings/passkeys');
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
        const user = passkeyTestUser(test.info().title);
        await registerPasskeyViaApi(page, user);

        await login(user, '/user-settings/passkeys');
        await expect(page.getByText('Your passkeys')).toBeVisible();
        await expect(page.getByText(user.email).first()).toBeVisible();

        // Delete the passkey
        await page.getByRole('button', { name: 'Delete' }).first().click();
        await page.getByTestId('delete-dialog-confirm-button').click();

        // Verify passkey is removed
        await expect(page.getByText('You have not registered any passkeys with Artemis yet.')).toBeVisible();
    });

    test('logs in with a registered passkey', async ({ page, virtualAuthenticator }) => {
        const user = passkeyTestUser(test.info().title);
        await disableConditionalMediation(page);
        await registerPasskeyViaApi(page, user);

        // Clear session and go to sign-in page
        await page.context().clearCookies();
        await page.goto('/sign-in');

        // Wait for the passkey login button to be stable before clicking
        await page.locator('#passkey-login-button').waitFor({ state: 'visible' });
        await page.locator('#passkey-login-button').click();

        // Verify login succeeded by checking navigation to courses
        await page.waitForURL('**/courses**');
    });

    test('conditional mediation login succeeds with a registered passkey', async ({ page, virtualAuthenticator }) => {
        const user = passkeyTestUser(test.info().title);
        await registerPasskeyViaApi(page, user);

        // Clear session and navigate to sign-in page.
        // The virtual authenticator (automaticPresenceSimulation: true) automatically resolves
        // the background navigator.credentials.get({ mediation: 'conditional' }) that the
        // home component starts in prefillPasskeysIfPossible(), driving login without any
        // explicit user interaction.
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await page.waitForURL('**/courses**');
    });

    test('cannot login with a passkey after it was deleted', async ({ page, login, virtualAuthenticator }) => {
        const user = passkeyTestUser(test.info().title);
        await disableConditionalMediation(page);
        await registerPasskeyViaApi(page, user);

        // Delete the passkey via API
        await login(user, '/courses');
        const passkeysResponse = await page.request.get(`${BASE_API}/core/passkey/user`);
        const passkeys = await passkeysResponse.json();
        for (const passkey of passkeys) {
            await page.request.delete(`${BASE_API}/core/passkey/${passkey.credentialId}`);
        }

        // Clear session and try to login with passkey
        await page.context().clearCookies();
        await page.goto('/sign-in');

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
    const authResponse = await page.request.post(`${BASE_API}/core/public/authenticate`, {
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
    const error = await page.evaluate(
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
                return `Getting registration options failed: ${optionsResponse.status} ${body}`;
            }
            const options: PublicKeyCredentialCreationOptionsJSON = await optionsResponse.json();

            // 2. Create credential via the virtual authenticator
            // The server returns JSON-serialized options; we convert base64url strings to ArrayBuffers and override user info.
            const credential = (await navigator.credentials.create({
                publicKey: {
                    ...options,
                    challenge: base64urlToBuffer(options.challenge),
                    user: {
                        id: new TextEncoder().encode(userId.toString()),
                        name: email,
                        displayName: email,
                    },
                    excludeCredentials: options.excludeCredentials?.map((c: PublicKeyCredentialDescriptorJSON) => ({
                        id: base64urlToBuffer(c.id),
                        type: c.type,
                        transports: c.transports,
                    })),
                } as unknown as PublicKeyCredentialCreationOptions,
            })) as PublicKeyCredential | null;

            if (!credential) {
                return 'navigator.credentials.create() returned null';
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
                return `Registering credential failed: ${registerResponse.status} ${body}`;
            }
            return undefined;
        },
        { userId: account.id, email: account.email },
    );

    if (error) {
        throw new Error(`registerPasskeyViaApi failed: ${error}`);
    }
}
