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
        expect(await this.page.isVisible('#footer')).toBe(true);
    }

    /**
     * Asserts that the "About Us" link in the footer is visible and has the correct href attribute.
     */
    async shouldShowAboutUsInFooter() {
        expect(await this.page.isVisible('#about')).toBe(true);
        expect(await this.page.getAttribute('#about', 'href')).toBe('/about');
    }

    /**
     * Asserts that the "Request Change" link in the footer is visible and has a non-empty href attribute.
     */
    async shouldShowRequestChangeInFooter() {
        expect(await this.page.isVisible('#feedback')).toBe(true);
        expect(await this.page.getAttribute('#feedback', 'href')).toBeTruthy();
    }

    /**
     * Asserts that the "Release Notes" link in the footer is visible and has a non-empty href attribute.
     */
    async shouldShowReleaseNotesInFooter() {
        expect(await this.page.isVisible('#releases')).toBe(true);
        expect(await this.page.getAttribute('#releases', 'href')).toBeTruthy();
    }

    /**
     * Asserts that the "Privacy Statement" link in the footer is visible and has the correct href attribute.
     */
    async shouldShowPrivacyStatementInFooter() {
        expect(await this.page.isVisible('#privacy')).toBe(true);
        expect(await this.page.getAttribute('#privacy', 'href')).toBe('/privacy');
    }

    /**
     * Asserts that the "Imprint" link in the footer is visible and has the correct href attribute.
     */
    async shouldShowImprintInFooter() {
        expect(await this.page.isVisible('#imprint')).toBe(true);
        expect(await this.page.getAttribute('#imprint', 'href')).toBe('/imprint');
    }
}
