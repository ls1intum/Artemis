import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { studentOne } from '../support/users';
import { BASE_API } from '../support/constants';

test.describe('Login page tests', { tag: '@fast' }, () => {
    test('Logs in via the UI', async ({ page, loginPage }) => {
        await page.goto('/sign-in');
        await loginPage.login(studentOne);
        await page.waitForURL('**/courses**');

        const cookies = await page.context().cookies();
        const jwtCookie = cookies.find((cookie) => cookie.name === 'jwt');
        expect(jwtCookie).toBeDefined();
        expect(jwtCookie?.httpOnly).toBe(true);
        expect(jwtCookie?.sameSite).toBe('Lax');
    });

    test('Logs in programmatically and logs out via the UI', async ({ page, login, navigationBar }) => {
        await login(studentOne, '/courses');
        await page.waitForURL('**/courses**');

        const responsePromise = page.waitForResponse(`${BASE_API}/core/public/logout`);
        await navigationBar.logout();
        await responsePromise;

        const cookies = await page.context().cookies();
        const jwtCookie = cookies.find((cookie) => cookie.name === 'jwt');
        expect(jwtCookie).toBeUndefined();
    });

    test('Displays error messages on wrong password', async ({ page, loginPage }) => {
        await page.goto('/sign-in');
        await loginPage.login({ username: 'some_user_name', password: 'lorem-ipsum' });

        await page.waitForURL('/sign-in');

        const alertElement = await page.waitForSelector('tum-ui-message');
        expect(await alertElement.isVisible()).toBeTruthy();
        const alertText = await alertElement.textContent();
        expect(alertText).toContain('Failed to sign in! Please check your login and password and try again.');
    });

    test('Fails to access protected resource without login', async ({ page }) => {
        await page.goto('/course-management');
        await page.waitForURL('/sign-in');
    });

    test('Requests the login options for a known internal user and continues to the password step', async ({ page, loginPage }) => {
        await page.goto('/sign-in');
        await loginPage.enterUsername(studentOne.username);

        const responsePromise = page.waitForResponse((response) => response.url().includes(`${BASE_API}/core/public/login-options`));
        await loginPage.clickContinueButton();
        const response = await responsePromise;

        expect(response.status()).toBe(200);
        const options = await response.json();
        expect(options.loginMethod).toBe('PASSWORD');
        expect(options.idpName ?? null).toBeNull();

        // internal users authenticate with their Artemis password, so the second stage has to offer the password field
        await expect(page.locator('#password')).toBeVisible();
    });

    test('Requests the login options by email address', async ({ page, login, loginPage }) => {
        // the seeded email address is not hard coded anywhere, so read it from the account of the logged in user
        await login(studentOne, '/courses');
        const accountResponse = await page.request.get(`${BASE_API}/core/public/account`);
        expect(accountResponse.status()).toBe(200);
        const email = (await accountResponse.json()).email;
        expect(email).toBeTruthy();

        // return to the anonymous login page and identify via the email address instead of the login
        await page.context().clearCookies();
        await page.goto('/sign-in');
        await loginPage.enterUsername(email);

        const responsePromise = page.waitForResponse((response) => response.url().includes(`${BASE_API}/core/public/login-options`));
        await loginPage.clickContinueButton();
        const response = await responsePromise;

        expect(response.status()).toBe(200);
        const options = await response.json();
        expect(options.loginMethod).toBe('PASSWORD');
        expect(options.idpName ?? null).toBeNull();
        await expect(page.locator('#password')).toBeVisible();
    });

    test('Answers identically for an unknown account so it cannot be used to enumerate users', async ({ page, loginPage }) => {
        await page.goto('/sign-in');
        await loginPage.enterUsername('artemis_test_user_does_not_exist');

        const responsePromise = page.waitForResponse((response) => response.url().includes(`${BASE_API}/core/public/login-options`));
        await loginPage.clickContinueButton();
        const response = await responsePromise;

        // this test server has no LDAP and no external identity provider, so an unknown account has to look exactly like a known internal one
        expect(response.status()).toBe(200);
        const options = await response.json();
        expect(options.loginMethod).toBe('PASSWORD');
        expect(options.idpName ?? null).toBeNull();
        await expect(page.locator('#password')).toBeVisible();
    });

    test('Verify footer content', async ({ page, loginPage }) => {
        await page.goto('/sign-in');
        await loginPage.shouldShowFooter();
        await loginPage.shouldShowAboutUsInFooter();
        await loginPage.shouldShowRequestChangeInFooter();
        await loginPage.shouldShowReleaseNotesInFooter();
        await loginPage.shouldShowPrivacyStatementInFooter();
        await loginPage.shouldShowImprintInFooter();
    });
});
