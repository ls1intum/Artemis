import { UserCredentials } from './users';
import { BASE_API } from './constants';
import { Page, expect } from '@playwright/test';

/**
 * A class that encapsulates static helper command methods.
 */
export class Commands {
    /**
     * Logs in using API.
     * @param page - Playwright page object.
     * @param credentials - UserCredentials object containing username and password.
     * @param url - Optional URL to navigate to after successful login.
     */
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        await Commands.logout(page);
        const { username, password } = credentials;

        const jwtCookie = await page
            .context()
            .cookies()
            .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        if (!jwtCookie) {
            const response = await page.request.post(`${BASE_API}/public/authenticate`, {
                data: {
                    username,
                    password,
                    rememberMe: true,
                },
                failOnStatusCode: false,
            });

            expect(response.status()).toBe(200);

            const newJwtCookie = await page
                .context()
                .cookies()
                .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
            expect(newJwtCookie).not.toBeNull();
        }

        if (url) {
            await page.goto(url);
        }
    };

    static logout = async (page: Page): Promise<void> => {
        await page.request.post(`${BASE_API}/public/logout`);
    };

    static reloadUntilFound = async (page: Page, selector: string, interval = 2000, timeout = 20000) => {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            try {
                await page.waitForSelector(selector, { timeout: interval });
                return;
            } catch (error) {
                await page.reload();
            }
        }

        throw new Error(`Timed out finding an element matching the "${selector}" selector`);
    };
}
