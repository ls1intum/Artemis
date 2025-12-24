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
    // Websocket connection may take longer to establish, handled separately with longer timeout
    { selector: '#websocketConnection', name: 'websocket connection', expectedStatus: 'Connected', timeout: 30000 },
];

test.describe('Check artemis system health', { tag: '@fast' }, () => {
    let page: Page;

    test.beforeAll('Login as admin and visit system health page', async ({ browser }) => {
        page = await browser.newPage();
        await Commands.login(page, admin, '/admin/health');
        // Wait for the page to fully load and websocket to establish
        await page.waitForLoadState('networkidle');
    });

    for (const healthCheck of healthChecks) {
        test(`Checks ${healthCheck.name} health`, async () => {
            const statusLocator = page.locator(`#healthCheck ${healthCheck.selector} .status`);
            const timeout = healthCheck.timeout || 5000;
            await expect(statusLocator).toHaveText(healthCheck.expectedStatus, { timeout });
        });
    }
});
