import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { Page, expect } from '@playwright/test';
import { Commands } from '../support/commands';

const healthChecks = [
    { selector: '#continuousIntegrationServer', name: 'continuous integration server', expectedStatus: 'UP' },
    { selector: '#versionControlServer', name: 'version control server', expectedStatus: 'UP' },
    { selector: '#db', name: 'db', expectedStatus: 'UP' },
    { selector: '#hazelcast', name: 'hazelcast', expectedStatus: 'UP' },
    { selector: '#ping', name: 'ping', expectedStatus: 'UP' },
    { selector: '#readinessState', name: 'readiness state', expectedStatus: 'UP' },
    { selector: '#websocketBroker', name: 'websocket broker', expectedStatus: 'UP' },
    { selector: '#websocketConnection', name: 'websocket connection', expectedStatus: 'Connected' },
];

test.describe('Check artemis system health', { tag: '@fast' }, () => {
    let page: Page;

    test.beforeAll('Login as admin and visit system health page', async ({ browser }) => {
        page = await browser.newPage();
        await Commands.login(page, admin, '/admin/health');
    });

    for (const healthCheck of healthChecks) {
        test(`Checks ${healthCheck.name} health`, async () => {
            await expect(page.locator(`#healthCheck ${healthCheck.selector} .status`)).toHaveText(healthCheck.expectedStatus);
        });
    }
});
