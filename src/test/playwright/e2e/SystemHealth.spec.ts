import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { Page, expect } from '@playwright/test';
import { Commands } from '../support/commands';

const healthChecks = [{ selector: '#websocketConnection', name: 'websocket connection', expectedStatus: 'Connected' }];

test.describe('Check artemis system health', () => {
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
