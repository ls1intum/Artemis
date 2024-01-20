import { UserCredentials } from './users';
import { BASE_API } from '../../cypress/support/constants';
import { Page, expect } from '@playwright/test';

export class Commands {
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        const { username, password } = credentials;

        const jwtCookie = await page
            .context()
            .cookies()
            .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        if (!jwtCookie) {
            const response = await page.request.post(BASE_API + 'public/authenticate', {
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
}
