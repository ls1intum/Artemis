import { UserCredentials } from './users';
import { BASE_API } from '../../cypress/support/constants';
import { Page, expect } from '@playwright/test';
import axiosBase, { AxiosResponse } from 'axios';
import * as https from 'https';
import * as fs from 'fs';

function parseCookie(setCookieString: string) {
    const parts = setCookieString.split(';').map((p) => p.trim());
    const pair = parts[0].split('=');
    return {
        name: pair[0],
        value: pair[1],
        url: `${process.env.baseURL}/`,
    };
}

export class Commands {
    private static axios = axiosBase.create({
        httpsAgent: new https.Agent({
            ca: fs.readFileSync('./certs/rootCA.pem'),
            cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
            key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
            // rejectUnauthorized: false
        }),
    });

    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        // axios implementation
        const { username, password } = credentials;

        // Check for existing JWT cookie
        const jwtCookie = await page
            .context()
            .cookies()
            .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        if (!jwtCookie) {
            // Making a POST request using Axios
            const response: AxiosResponse = await this.axios.post(`${process.env.baseURL}/${BASE_API}public/authenticate`, {
                username,
                password,
                rememberMe: true,
            });

            // Asserting that the response status is 200
            expect(response.status).toBe(200);

            // Extracting set-cookie header and asserting its presence
            const setCookieHeader = response.headers['set-cookie'];
            expect(setCookieHeader).toBeDefined();

            // Parsing and adding cookies to Playwright context
            if (setCookieHeader) {
                const cookies = setCookieHeader.map(parseCookie);
                await page.context().addCookies(cookies);
            }
        }

        if (url) {
            await page.goto(url);
        }

        // plawright apirequestcontext implementation
        // const { username, password } = credentials;
        //
        // const jwtCookie = await page
        //     .context()
        //     .cookies()
        //     .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        // if (!jwtCookie) {
        //     const response = await page.request.post(BASE_API + 'public/authenticate', {
        //         data: {
        //             username,
        //             password,
        //             rememberMe: true,
        //         },
        //         failOnStatusCode: false,
        //     });
        //
        //     expect(response.status()).toBe(200);
        //
        //     const newJwtCookie = await page
        //         .context()
        //         .cookies()
        //         .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        //     expect(newJwtCookie).not.toBeNull();
        // }
        //
        // if (url) {
        //     await page.goto(url);
        // }

        // node.js https implementation
        // const { username, password } = credentials;
        //
        // const jwtCookie = await page
        //     .context()
        //     .cookies()
        //     .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        // if (!jwtCookie) {
        //     await new Promise((resolve, reject) => {
        //         const fullUrl = new URL(`${process.env.baseURL}/${BASE_API}public/authenticate`);
        //         console.log('Login URL:', fullUrl);
        //
        //         const reqData = JSON.stringify({
        //             username,
        //             password,
        //             rememberMe: true,
        //         });
        //
        //         console.log('Request Data:', reqData);
        //         const req = https.request(
        //             {
        //                 hostname: fullUrl.hostname,
        //                 port: fullUrl.port.length != 0 ? fullUrl.port : null,
        //                 path: `/${BASE_API}public/authenticate`,
        //                 method: 'POST',
        //                 agent: new https.Agent({
        //                     ca: fs.readFileSync('./certs/rootCA.pem'),
        //                     cert: fs.readFileSync('./certs/artemis-nginx+4.pem'),
        //                     key: fs.readFileSync('./certs/artemis-nginx+4-key.pem'),
        //                 }),
        //                 headers: {
        //                     'Content-Type': 'application/json',
        //                     'User-Agent': 'Playwright',
        //                 },
        //             },
        //             (res) => {
        //                 let data = '',
        //                     jwtCookie = '';
        //                 res.on('data', (chunk) => (data += chunk));
        //                 res.on('end', async () => {
        //                     console.log('Response:', data);
        //                     expect(res.statusCode).toBe(200);
        //                     console.log('Status code' + res.statusCode);
        //                     const setCookieHeader = res.headers['set-cookie'];
        //                     if (setCookieHeader) {
        //                         setCookieHeader.forEach((cookie) => {
        //                             if (cookie.startsWith('jwt=')) {
        //                                 jwtCookie = cookie.split(';')[0];
        //                             }
        //                         });
        //                     }
        //
        //                     if (jwtCookie) {
        //                         console.log('Manually setting cookie: ' + jwtCookie);
        //                         const [name, value] = jwtCookie.split('=');
        //                         console.log('Manually setting cookie: ' + name + ' ' + value);
        //                         await page.context().addCookies([
        //                             {
        //                                 name,
        //                                 value,
        //                                 url: `${process.env.baseURL}/`,
        //                             },
        //                         ]);
        //                     }
        //                     resolve(data);
        //                 });
        //             },
        //         );
        //
        //         req.on('error', (e) => {
        //             console.error(`Request error: ${e.message}`);
        //             reject(e);
        //         });
        //         req.write(reqData);
        //         req.end();
        //     });
        //
        //     console.log('Checking cookies...');
        //
        //     const newJwtCookie = await page
        //         .context()
        //         .cookies()
        //         .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        //
        //     console.log('Cookie: ' + newJwtCookie);
        //     console.log('Token is defined');
        //     if (!newJwtCookie) {
        //         throw new Error('Login failed: JWT cookie not found after login');
        //     }
        // }
        //
        // console.log('Navigating to page...');
        //
        // if (url) {
        //     await page.goto(url);
        // }
    };
}
