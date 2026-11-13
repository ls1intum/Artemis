import { expect, request, test } from '@playwright/test';
import { studentOne } from '../../support/users';

/**
 * The headers and cookie flags that the server promises. A change of the reverse proxy or the security configuration that drops one of them is
 * found here, before a browser stops enforcing it.
 */
test.describe('Security headers and session cookie', { tag: '@fast' }, () => {
    test('The client is served with the headers that restrict the browser', async ({ baseURL }) => {
        // The local fast runner serves the client with the Angular development server, which answers this document without the headers of Artemis.
        test.skip(new URL(baseURL ?? 'http://localhost').port === '9000', 'the client is served by the Angular development server');
        const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
        try {
            const response = await context.get('/');
            expect(response.ok()).toBe(true);
            const headers = response.headers();
            expect(headers['x-content-type-options'], 'the content type is not guessed').toBe('nosniff');
            expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
            expect(headers['content-security-policy']).toContain("script-src 'self'");
            expect(headers['permissions-policy']).toContain('camera=()');
            expect(headers['permissions-policy']).toContain('geolocation=()');
        } finally {
            await context.dispose();
        }
    });

    test('A sign-in sets an HTTP-only cookie with a same-site restriction', async ({ baseURL }) => {
        const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
        try {
            const response = await context.post('/api/core/public/authenticate', {
                data: { username: studentOne.username, password: studentOne.password, rememberMe: false },
            });
            expect(response.ok()).toBe(true);
            const cookies = response.headersArray().filter((header) => header.name.toLowerCase() === 'set-cookie');
            const jwt = cookies.map((header) => header.value).find((value) => value.startsWith('jwt='));
            expect(jwt, 'the sign-in sets the jwt cookie').toBeDefined();
            expect(jwt).toMatch(/;\s*HttpOnly/i);
            expect(jwt).toMatch(/;\s*SameSite=Lax/i);
            expect(jwt).toMatch(/;\s*Path=\//i);
        } finally {
            await context.dispose();
        }
    });

    test('Responses with account data are not cached', async ({ baseURL }) => {
        const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
        try {
            const signIn = await context.post('/api/core/public/authenticate', {
                data: { username: studentOne.username, password: studentOne.password, rememberMe: false },
            });
            expect(signIn.ok()).toBe(true);
            const account = await context.get('/api/core/public/account');
            expect(account.ok()).toBe(true);
            const cacheControl = account.headers()['cache-control'] ?? '';
            expect(cacheControl).toContain('no-store');
        } finally {
            await context.dispose();
        }
    });

    test('A request without a session is refused by a protected endpoint', async ({ baseURL }) => {
        const context = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
        try {
            const path = '/api/course/courses/for-dashboard';
            const response = await context.get(path);
            expect([401, 403]).toContain(response.status());

            // The same path answers a signed-in user, so the refusal above is not caused by a path that does not exist
            const signIn = await context.post('/api/core/public/authenticate', {
                data: { username: studentOne.username, password: studentOne.password, rememberMe: false },
            });
            expect(signIn.ok()).toBe(true);
            const signedIn = await context.get(path);
            expect(signedIn.ok()).toBe(true);
        } finally {
            await context.dispose();
        }
    });
});
