import { Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Landing Page.
 */
export class LandingPage {
    private page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Enters the specified username and password into the keycloak login form, then clicks the login button.
     * @param username the username to be entered on keycloak.
     * @param password the password to be entered on keycloak.
     * @private
     */
    async login(username: string, password: string) {
        await this.clickLoginButton();
        await this.page.getByRole('textbox', { name: 'Username' }).fill(username);
        await this.page.getByRole('textbox', { name: 'Password' }).fill(password);
        await this.page.getByRole('button', { name: 'Sign in' }).click();
    }

    private async clickLoginButton() {
        return this.page.getByRole('button', { name: 'Login' }).click();
    }

    setPage(page: Page) {
        this.page = page;
    }
}
