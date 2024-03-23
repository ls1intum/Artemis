import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { studentOne } from '../support/users';
import { BASE_API } from '../support/constants';

test.describe('Login page tests', () => {
    test('Logs in via the UI', async ({ page, loginPage }) => {
        await page.goto('/');
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

        const responsePromise = page.waitForResponse(`${BASE_API}/public/logout`);
        await navigationBar.logout();
        await responsePromise;

        const cookies = await page.context().cookies();
        const jwtCookie = cookies.find((cookie) => cookie.name === 'jwt');
        expect(jwtCookie).toBeUndefined();
    });

    test('Displays error messages on wrong password', async ({ page, loginPage }) => {
        await page.goto('/');
        await loginPage.login({ username: 'some_user_name', password: 'lorem-ipsum' });

        await page.waitForURL('/');

        const alertElement = await page.waitForSelector('.alert');
        expect(await alertElement.isVisible()).toBeTruthy();
        const alertText = await alertElement.textContent();
        expect(alertText).toContain('Failed to sign in! Please check your username and password and try again.');

        await page.click('#login-button');
        await page.click('#login-button');
    });

    test('Fails to access protected resource without login', async ({ page }) => {
        await page.goto('/course-management');
        await page.waitForURL('/');
    });

    test('Verify footer content', async ({ page, loginPage }) => {
        await page.goto('/');
        await loginPage.shouldShowFooter();
        await loginPage.shouldShowAboutUsInFooter();
        await loginPage.shouldShowRequestChangeInFooter();
        await loginPage.shouldShowReleaseNotesInFooter();
        await loginPage.shouldShowPrivacyStatementInFooter();
        await loginPage.shouldShowImprintInFooter();
    });
});
