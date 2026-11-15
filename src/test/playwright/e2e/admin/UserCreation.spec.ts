import { expect } from '@playwright/test';

import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { generateUUID } from '../../support/utils';

test.describe('Internal user creation', { tag: '@fast' }, () => {
    for (const toggleBack of [false, true]) {
        test(`creates an internal user with ${toggleBack ? 'random password reselected after typing a password' : 'the default random password'}`, async ({
            page,
            login,
            userManagementAPIRequests,
        }) => {
            const userLogin = `pw_random_${generateUUID().slice(0, 8)}`;
            await login(admin, '/admin/user-management/new');

            try {
                await page.locator('#login').fill(userLogin);
                await page.locator('#firstName').fill('Random');
                await page.locator('#lastName').fill('Password');
                await page.locator('#email').fill(`${userLogin}@example.com`);
                await page.getByLabel('Internal', { exact: true }).check();

                const randomPassword = page.getByLabel('Random password', { exact: true });
                await expect(randomPassword).toBeChecked();
                if (toggleBack) {
                    await randomPassword.uncheck();
                    await page.locator('#password').fill('Typed-password-123');
                    await randomPassword.check();
                }

                const creationResponse = page.waitForResponse((response) => response.url().endsWith('/api/account/admin/users') && response.request().method() === 'POST');
                await page.getByTestId('save-user-button').click();
                const response = await creationResponse;
                expect(response.request().postDataJSON()).not.toHaveProperty('password');
                expect(response.status()).toBe(201);
                await page.waitForURL('**/admin/user-management');

                const persistedUser = await userManagementAPIRequests.getUser(userLogin);
                expect(persistedUser.status()).toBe(200);
                expect(await persistedUser.json()).toMatchObject({ login: userLogin, internal: true });
            } finally {
                const deletionResponse = await userManagementAPIRequests.deleteUser(userLogin);
                expect([200, 404]).toContain(deletionResponse.status());
            }
        });
    }
});
