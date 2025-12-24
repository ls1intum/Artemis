import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { Page, expect } from '@playwright/test';
import { Commands } from '../support/commands';

const healthChecks = [
    { selector: '#continuousIntegrationServer', name: 'continuous integration server', expectedStatus: 'UP' },
    { selector: '#db', name: 'db', expectedStatus: 'UP' },
    { selector: '#hazelcast', name: 'hazelcast', expectedStatus: 'UP' },
    { selector: '#ping', name: 'ping', expectedStatus: 'UP' },
    { selector: '#readinessState', name: 'readiness state', expectedStatus: 'UP' },
    { selector: '#websocketBroker', name: 'websocket broker', expectedStatus: 'UP' },
];

test.describe('Check artemis system health', { tag: '@fast' }, () => {
    let page: Page;

    test.beforeAll('Login as admin and visit system health page', async ({ browser }) => {
        page = await browser.newPage();
        await Commands.login(page, admin, '/admin/health');
        // Wait for the page to fully load
        await page.waitForLoadState('networkidle');
    });

    for (const healthCheck of healthChecks) {
        test(`Checks ${healthCheck.name} health`, async () => {
            const statusLocator = page.locator(`#healthCheck ${healthCheck.selector} .status`);
            await expect(statusLocator).toHaveText(healthCheck.expectedStatus, { timeout: 5000 });
        });
    }

    // WebSocket connection test handled separately with reload mechanism since
    // the client-side WebSocket connection may take longer to establish on CI
    test('Checks websocket connection health', async () => {
        const statusLocator = page.locator('#healthCheck #websocketConnection .status');
        const timeout = 60000;
        const reloadInterval = 5000;
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            try {
                await expect(statusLocator).toHaveText('Connected', { timeout: reloadInterval });
                return; // Success
            } catch {
                // WebSocket not connected yet, reload and try again
                if (Date.now() - startTime + reloadInterval < timeout) {
                    await page.reload();
                    await page.waitForLoadState('networkidle');
                }
            }
        }

        // Final check with remaining time
        await expect(statusLocator).toHaveText('Connected', { timeout: 5000 });
    });
});
