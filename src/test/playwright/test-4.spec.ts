import { test } from './support/fixtures';
import { expect } from '@playwright/test';

test('test', async ({ page }) => {
    await page.goto('http://localhost:9000/');
    await page.getByRole('button', { name: 'Log in' }).click();
    await page.getByRole('textbox', { name: 'Login or email' }).fill('artemis_admin');
    await page.getByRole('textbox', { name: 'Login or email' }).press('Tab');
    await page.getByRole('textbox', { name: 'Password' }).fill('artemis');
    await page.getByRole('textbox', { name: 'Password' }).press('ControlOrMeta+a');
    await page.getByRole('textbox', { name: 'Password' }).fill('artemis_admin');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('button', { name: 'Set Up Passkey' }).click();
    await page.locator('jhi-close-circle path').click();
    await page.locator('#account-menu').click();
    await page.getByRole('link', { name: 'Settings' }).click();
    await page.getByRole('link', { name: 'Passkeys' }).click();
    await page.getByRole('button', { name: 'Set Up Later' }).click();
    await page.getByRole('button', { name: 'Add new passkey' }).click();
});
