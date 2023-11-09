import { Page, expect } from '@playwright/test';

export class LoginPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async enterUsername(name: string) {
        await this.page.fill('#username', name);
    }

    async enterPassword(password: string) {
        await this.page.fill('#password', password);
    }

    async clickLoginButton() {
        await this.page.click('#login-button');
    }

    async login(credentials: UserCredentials) {
        await this.enterUsername(credentials.username);
        await this.enterPassword(credentials.password);
        await this.clickLoginButton();
    }

    async shouldShowFooter() {
        expect(await this.page.isVisible('#footer')).toBe(true);
    }

    async shouldShowAboutUsInFooter() {
        expect(await this.page.isVisible('#about')).toBe(true);
        expect(await this.page.getAttribute('#about', 'href')).toBe('/about');
    }

    async shouldShowRequestChangeInFooter() {
        expect(await this.page.isVisible('#request-change')).toBe(true);
        expect(await this.page.getAttribute('#request-change', 'href')).toBeTruthy();
    }

    async shouldShowReleaseNotesInFooter() {
        expect(await this.page.isVisible('#release-notes')).toBe(true);
        expect(await this.page.getAttribute('#release-notes', 'href')).toBeTruthy();
    }

    async shouldShowPrivacyStatementInFooter() {
        expect(await this.page.isVisible('#privacy')).toBe(true);
        expect(await this.page.getAttribute('#privacy', 'href')).toBe('/privacy');
    }

    async shouldShowImprintInFooter() {
        expect(await this.page.isVisible('#imprint')).toBe(true);
        expect(await this.page.getAttribute('#imprint', 'href')).toBe('/imprint');
    }
}

export interface UserCredentials {
    username: string;
    password: string;
}
