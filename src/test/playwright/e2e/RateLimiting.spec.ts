import { test, expect } from '@playwright/test';

test.describe('Rate Limiting', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.waitForTimeout(2000);
    });

    test('should enforce rate limiting on registration endpoint', async ({ page }) => {
        await page.goto('/account/register');

        // Fill out registration form multiple times rapidly
        const registrationAttempts = [];

        for (let i = 0; i < 10; i++) {
            const attempt = page.request.post('/api/core/public/register', {
                data: {
                    login: `artemis_test_user_${i}`,
                    email: `artemis_test_user_${i}@tum.de`,
                    password: 'artemis_test_user_${i}',
                    firstName: 'Test',
                    lastName: `User_${i}`,
                    langKey: 'en',
                },
                headers: {
                    'Content-Type': 'application/json',
                },
            });
            registrationAttempts.push(attempt);
        }

        // Wait for all requests to complete
        const responses = await Promise.all(registrationAttempts);

        // Check that some requests were rate limited (429 status)
        const rateLimitedResponses = responses.filter((response) => response.status() === 429);
        const successfulResponses = responses.filter((response) => response.status() < 400);

        // Expect that rate limiting kicked in
        expect(rateLimitedResponses.length).toBeGreaterThan(0);
        expect(successfulResponses.length).toBeLessThan(10);

        // Check rate limit headers
        const rateLimitedResponse = rateLimitedResponses[0];
        expect(rateLimitedResponse.status()).toBe(429);

        // The response should contain retry-after information as plain text
        const responseText = await rateLimitedResponse.text();
        expect(responseText).toContain('Too Many Requests');
    });

    test('should enforce rate limiting on password reset endpoint', async ({ page }) => {
        // Make multiple password reset requests rapidly
        const resetAttempts = [];

        for (let i = 0; i < 15; i++) {
            const attempt = page.request.post('/api/core/public/account/reset-password/init', {
                data: `artemis_test_user_${i}@tum.de`,
                headers: {
                    'Content-Type': 'application/json',
                },
            });
            resetAttempts.push(attempt);
        }

        // Wait for all requests to complete
        const responses = await Promise.all(resetAttempts);

        // Check that some requests were rate limited
        const rateLimitedResponses = responses.filter((response) => response.status() === 429);

        // With login-related endpoints having higher limits, we should still see some rate limiting
        expect(rateLimitedResponses.length).toBeGreaterThan(0);
    });

    test('should allow requests within rate limit', async ({ page }) => {
        // Add unique timestamp to avoid conflicts with other tests
        const timestamp = Date.now();

        // Wait a bit to ensure clean slate
        await page.waitForTimeout(1000);

        const response = await page.request.post('/api/core/public/register', {
            data: {
                login: `artemis_test_user_${timestamp}`,
                email: `artemis_test_user_${timestamp}@tum.de`,
                password: `artemis_test_user_${timestamp}`,
                firstName: 'Test',
                lastName: `User_${timestamp}`,
                langKey: 'en',
            },
            headers: {
                'Content-Type': 'application/json',
                'X-Forwarded-For': `10.0.0.${Math.floor(Math.random() * 255) + 1}`,
            },
        });

        // Should not be rate limited for a single request from unique IP
        expect(response.status()).not.toBe(429);
    });

    test('should respect X-Forwarded-For header for client identification', async ({ page }) => {
        // Add longer delay to ensure clean state and avoid interference from previous tests
        await page.waitForTimeout(10000);

        const timestamp = Date.now();

        const response = await page.request.post('/api/core/public/register', {
            data: {
                login: `artemis_test_user_${timestamp}_1`,
                email: `artemis_test_user_${timestamp}_1@tum.de`,
                password: `artemis_test_user_${timestamp}`,
                firstName: 'Test',
                lastName: `User_${timestamp}_1`,
                langKey: 'en',
            },
            headers: {
                'Content-Type': 'application/json',
                'X-Forwarded-For': `203.0.113.${Math.floor(Math.random() * 255) + 1}`, // Use test IP range
            },
        });

        // make another request with a different IP after a delay
        await page.waitForTimeout(2000);

        const response2 = await page.request.post('/api/core/public/register', {
            data: {
                login: `artemis_test_user_${timestamp}_2`,
                email: `artemis_test_user_${timestamp}_2@tum.de`,
                password: `artemis_test_user_${timestamp}`,
                firstName: 'Test',
                lastName: `User_${timestamp}_2`,
                langKey: 'en',
            },
            headers: {
                'Content-Type': 'application/json',
                'X-Forwarded-For': `203.0.114.${Math.floor(Math.random() * 255) + 1}`, // Different IP range
            },
        });

        // We expect business logic responses (200, 400, 403), depending on whether registration is enabled but NOT rate limiting (429)
        expect(response.status()).not.toBe(429);
        expect(response2.status()).not.toBe(429);

        expect(response.status()).not.toBe(500);
        expect(response2.status()).not.toBe(500);
    });

    test('should handle IPv6 addresses correctly', async ({ page }) => {
        const timestamp = Date.now();

        const response = await page.request.post('/api/core/public/register', {
            data: {
                login: `artemis_test_user_${timestamp}`,
                email: `artemis_test_user_${timestamp}@tum.de`,
                password: `artemis_test_user_${timestamp}`,
                firstName: 'Test',
                lastName: `User_${timestamp}`,
                langKey: 'en',
            },
            headers: {
                'Content-Type': 'application/json',
                'X-Forwarded-For': '[2001:db8::1]:8080',
            },
        });

        expect(response.status()).not.toBe(500);
    });

    test('should clean up IP addresses with ports', async ({ page }) => {
        // Add delay to ensure clean state
        await page.waitForTimeout(1000);

        // Make multiple requests from the same IP but different ports
        const requests = [];
        const timestamp = Date.now();

        for (let i = 0; i < 8; i++) {
            const request = page.request.post('/api/core/public/register', {
                data: {
                    login: `artemis_test_user_${timestamp}_${i}`,
                    email: `artemis_test_user_${timestamp}_${i}@tum.de`,
                    password: `artemis_test_user_${timestamp}_${i}`,
                    firstName: 'Test',
                    lastName: `User_${timestamp}_${i}`,
                    langKey: 'en',
                },
                headers: {
                    'Content-Type': 'application/json',
                    'X-Forwarded-For': `192.168.100.50:${8080 + i}`, // Use specific IP range
                },
            });
            requests.push(request);
        }

        const responses = await Promise.all(requests);

        // Should be rate limited since all requests are from the same IP (different ports)
        const rateLimitedResponses = responses.filter((response) => response.status() === 429);
        expect(rateLimitedResponses.length).toBeGreaterThan(0);
    });
});
