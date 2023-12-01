import { UserCredentials } from './users';
import { BASE_API } from '../../cypress/support/constants';
import { Page, expect } from '@playwright/test';
import * as https from 'https';
import fs from 'fs';

export class Commands {
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        const { username, password } = credentials;

        const jwtCookie = await page
            .context()
            .cookies()
            .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        if (!jwtCookie) {
            await new Promise((resolve, reject) => {
                console.log('Login URL: ' + process.env.baseURL + '/' + BASE_API + 'public/authenticate');
                const req = https.request(
                    {
                        hostname: process.env.baseURL,
                        path: BASE_API + 'public/authenticate',
                        method: 'POST',
                        agent: new https.Agent({
                            ca: fs.readFileSync('./certs/rootCA.pem'),
                            cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
                            key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
                        }),
                        headers: {
                            'Content-Type': 'application/json',
                        },
                    },
                    (res) => {
                        let data = '';
                        res.on('data', (chunk) => (data += chunk));
                        res.on('end', () => {
                            console.log('Response:', data);

                            expect(res.statusCode).toBe(200);
                        });
                    },
                );

                req.on('error', reject);
                req.write(
                    JSON.stringify({
                        username,
                        password,
                        rememberMe: true,
                    }),
                );
                req.end();
            });

            const newJwtCookie = await page
                .context()
                .cookies()
                .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
            if (!newJwtCookie) {
                throw new Error('Login failed: JWT cookie not found after login');
            }
        }

        if (url) {
            await page.goto(url);
        }
    };
}
