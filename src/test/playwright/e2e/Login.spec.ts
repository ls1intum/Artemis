import { Page, expect } from '@playwright/test';
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

    /**
     * Reads the login options straight from the API. The browser network buffer is deliberately not used: entries in it
     * can be evicted before the body is read, which fails with "No data found for resource with given identifier" on a
     * loaded runner even though the request itself succeeded.
     */
    async function fetchLoginOptions(page: Page, usernameOrEmail: string) {
        const response = await page.request.get(`${BASE_API}/core/public/login-options`, { params: { usernameOrEmail } });
        expect(response.status()).toBe(200);
        return response.json();
    }

    test('Requests the login options for a known internal user and continues to the password step', async ({ page, loginPage }) => {
        expect(await fetchLoginOptions(page, studentOne.username)).toEqual({ loginMethod: 'PASSWORD', idpName: null });

        await page.goto('/sign-in');
        await loginPage.enterUsername(studentOne.username);
        await loginPage.clickContinueButton();

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
        expect(await fetchLoginOptions(page, email)).toEqual({ loginMethod: 'PASSWORD', idpName: null });

        await page.goto('/sign-in');
        await loginPage.enterUsername(email);
        await loginPage.clickContinueButton();
        await expect(page.locator('#password')).toBeVisible();
    });

    test('Answers identically for an unknown account so it cannot be used to enumerate users', async ({ page, loginPage }) => {
        // this test server has no LDAP and no external identity provider, so an unknown account has to be answered
        // exactly like a known one. Comparing the two answers states that directly instead of restating the expected shape.
        const knownAccount = await fetchLoginOptions(page, studentOne.username);
        const unknownAccount = await fetchLoginOptions(page, 'artemis_test_user_does_not_exist');
        expect(unknownAccount).toEqual(knownAccount);

        // the second stage must not give it away either
        await page.goto('/sign-in');
        await loginPage.enterUsername('artemis_test_user_does_not_exist');
        await loginPage.clickContinueButton();
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
