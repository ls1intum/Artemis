import { Page, expect } from '@playwright/test';
import { UserCredentials } from '../users';

/**
 * A class which encapsulates UI selectors and actions for the Login Page.
 */
export class LoginPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Enters the specified value into the input field with the specified selector, then blurs the input field.
     * This is necessary because the username and password fields are only validated on blur, e.g. when the user clicks
     * elsewhere on the page or presses the Tab key.
     * @param selector The selector of the input field.
     * @param value The value to be entered.
     * @private
     */
    private async fillThenBlurInput(selector: string, value: string) {
        const locator = this.page.locator(selector);
        await locator.fill(value);
        await locator.blur();
    }

    /**
     * Enters the specified username into the username input field, then blurs the input field.
     * @param name - The username to be entered.
     */
    async enterUsername(name: string) {
        await this.fillThenBlurInput('#username', name);
    }

    /**
     * Enters the specified password into the password input field, then blurs the input field.
     * @param password - The password to be entered.
     */
    async enterPassword(password: string) {
        await this.fillThenBlurInput('#password', password);
    }

    /**
     * Clicks the login button.
     */
    async clickLoginButton() {
        await this.page.click('#login-button');
    }

    /**
     * Logs in with the provided credentials by entering username, password, and clicking the login button.
     * @param credentials - UserCredentials object containing username and password.
     */
    async login(credentials: UserCredentials) {
        await this.enterUsername(credentials.username);
        await this.enterPassword(credentials.password);
        await this.clickLoginButton();
    }

    /**
     * Asserts that the footer is visible.
     */
    async shouldShowFooter() {
        await expect(this.page.locator('#footer')).toBeVisible();
    }

    async shouldShowAboutUsInFooter() {
        await expect(this.page.locator('#about')).toBeVisible();
        await expect(this.page.locator('#about')).toHaveAttribute('href', '/about');
    }

    async shouldShowRequestChangeInFooter() {
        await expect(this.page.locator('#feedback')).toBeVisible();
    }

    async shouldShowReleaseNotesInFooter() {
        await expect(this.page.locator('#releases')).toBeVisible();
    }

    async shouldShowPrivacyStatementInFooter() {
        await expect(this.page.locator('#privacy')).toBeVisible();
        await expect(this.page.locator('#privacy')).toHaveAttribute('href', '/privacy');
    }

    async shouldShowImprintInFooter() {
        await expect(this.page.locator('#imprint')).toBeVisible();
        await expect(this.page.locator('#imprint')).toHaveAttribute('href', '/imprint');
    }
}
