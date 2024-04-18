import { test } from '../support/fixtures';
import { admin } from '../support/users';
import { expect } from '@playwright/test';

test.describe('Check artemis system health', () => {
    test.beforeEach('Login as admin and visit system health page', async ({ login }) => {
        await login(admin, '/admin/health');
    });

    test('Checks continuous integration health', async ({ page }) => {
        await expect(page.locator('#healthCheck #continuousIntegrationServer .status')).toHaveText('UP');
    });

    test('Checks version control health', async ({ page }) => {
        await expect(page.locator('#healthCheck #versionControlServer .status')).toHaveText('UP');
    });

    test('Checks user management health', async ({ page }) => {
        await expect(page.locator('#healthCheck #userManagement .status')).toHaveText('UP');
    });

    test('Checks database health', async ({ page }) => {
        await expect(page.locator('#healthCheck #db .status')).toHaveText('UP');
    });

    test('Checks hazelcast health', async ({ page }) => {
        await expect(page.locator('#healthCheck #hazelcast .status')).toHaveText('UP');
    });

    test('Checks ping health', async ({ page }) => {
        await expect(page.locator('#healthCheck #ping .status')).toHaveText('UP');
    });

    test('Checks readiness health', async ({ page }) => {
        await expect(page.locator('#healthCheck #readinessState .status')).toHaveText('UP');
    });

    test('Checks websocket broker health', async ({ page }) => {
        await expect(page.locator('#healthCheck #websocketBroker .status')).toHaveText('UP');
    });

    test('Checks websocket connection health', async ({ page }) => {
        await expect(page.locator('#healthCheck #websocketConnection .status')).toHaveText('Connected');
    });
});
