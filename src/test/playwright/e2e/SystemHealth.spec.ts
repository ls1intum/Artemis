import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { Page, expect } from '@playwright/test';
import { Commands } from '../support/commands';

const healthChecks = [
    { id: 'continuousIntegrationServer', name: 'continuous integration server', expectedStatus: 'UP' },
    { id: 'versionControlServer', name: 'version control server', expectedStatus: 'UP' },
    { id: 'userManagement', name: 'user management', expectedStatus: 'UP' },
    { id: 'db', name: 'db', expectedStatus: 'UP' },
    { id: 'hazelcast', name: 'hazelcast', expectedStatus: 'UP' },
    { id: 'ping', name: 'ping', expectedStatus: 'UP' },
    { id: 'readinessState', name: 'readiness state', expectedStatus: 'UP' },
    { id: 'websocketBroker', name: 'websocket broker', expectedStatus: 'UP' },
    { id: 'websocketConnection', name: 'websocket connection', expectedStatus: 'Connected' },
];

test.describe('Check artemis system health', () => {
    let page: Page;

    test.beforeAll('Login as admin and visit system health page', async ({ browser }) => {
        page = await browser.newPage();
        await Commands.login(page, admin, '/admin/health');
        await page.waitForTimeout(5000);
        await page.waitForLoadState('networkidle');
    });

    for (const healthCheck of healthChecks) {
        test(`Checks ${healthCheck.name} health`, async () => {
            await expect(page.locator(`#healthCheck #${healthCheck.id} .status`)).toHaveText(healthCheck.expectedStatus);
        });
    }

    // test('Checks continuous integration health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #continuousIntegrationServer .status')).toHaveText('UP');
    // });
    //
    // test('Checks version control health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #versionControlServer .status')).toHaveText('UP');
    // });
    //
    // test('Checks user management health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #userManagement .status')).toHaveText('UP');
    // });
    //
    // test('Checks database health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #db .status')).toHaveText('UP');
    // });
    //
    // test('Checks hazelcast health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #hazelcast .status')).toHaveText('UP');
    // });
    //
    // test('Checks ping health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #ping .status')).toHaveText('UP');
    // });
    //
    // test('Checks readiness health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #readinessState .status')).toHaveText('UP');
    // });
    //
    // test('Checks websocket broker health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #websocketBroker .status')).toHaveText('UP');
    // });
    //
    // test('Checks websocket connection health', async ({ page }) => {
    //     await expect(page.locator('#healthCheck #websocketConnection .status')).toHaveText('Connected');
    // });
});
