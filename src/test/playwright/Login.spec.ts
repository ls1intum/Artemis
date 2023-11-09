import { expect, test } from '@playwright/test';
import { LoginPage, UserCredentials } from './LoginPage';

test.describe('Login page tests', () => {
    const studentOne: UserCredentials = { username: 'artemis_test_user_1', password: 'artemis_test_user_1' };

    test.beforeEach(async ({ page }) => {
        await page.goto('');
    });

    test('Logs in via the UI', async ({ page }) => {
        const loginPage = new LoginPage(page);
        await loginPage.login(studentOne);

        await page.waitForURL('**/courses');

        const cookies = await page.context().cookies();
        const jwtCookie = cookies.find((cookie) => cookie.name === 'jwt');
        expect(jwtCookie).toBeDefined();
        expect(jwtCookie.httpOnly).toBe(true);
        expect(jwtCookie.sameSite).toBe('Lax');
    });

    test('Displays error messages on wrong password', async ({ page }) => {
        const loginPage = new LoginPage(page);
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

    test('Verify footer content', async ({ page }) => {
        const loginPage = new LoginPage(page);
        await loginPage.shouldShowFooter();
        await loginPage.shouldShowAboutUsInFooter();
        await loginPage.shouldShowRequestChangeInFooter();
        await loginPage.shouldShowReleaseNotesInFooter();
        await loginPage.shouldShowPrivacyStatementInFooter();
        await loginPage.shouldShowImprintInFooter();
    });
});
