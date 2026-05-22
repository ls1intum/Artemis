import { test } from '../support/fixtures';
import { expect } from '@playwright/test';

test('Passkey registration via virtual authenticator', async ({ page, virtualAuthenticator }) => {
    // Login
    await page.goto('http://localhost:9000/');
    await page.getByRole('button', { name: 'Log in' }).click();
    await page.getByRole('textbox', { name: 'Login or email' }).fill('artemis_admin');
    await page.getByRole('textbox', { name: 'Login or email' }).press('Tab');
    await page.getByRole('textbox', { name: 'Password' }).fill('artemis_admin');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();

    // Click "Set Up Passkey" in the modal — the virtual authenticator handles the prompt
    await page.getByRole('button', { name: 'Set Up Passkey' }).click();

    // Verify no registration error alert is shown
    const errorAlert = page.locator('.alert-inner').getByText('The passkey could not be registered. Please try again.');
    await expect(errorAlert).not.toBeVisible();

    // Verify success alert is shown
    const successAlert = page.locator('.alert-inner').getByText('Your passkey has been successfully registered. You can manage your passkeys in the user settings');
    await expect(successAlert).toBeVisible();

    // Verify passkey was registered (modal should close, navigate to courses)
    await page.waitForURL('**/courses**');
});
