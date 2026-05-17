import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Commands } from '../../support/commands';
import { expect } from '@playwright/test';

/**
 * End-to-end test for the build-agent maintenance admin UI introduced alongside the build-container cache
 * cleanup (PR #12717).
 *
 * The flow exercised here is end-to-end through the multi-node architecture:
 * UI click on a core node → REST {@code /api/core/admin/agents/...} → core publishes on the maintenance Hazelcast
 * topic → every agent receives the broadcast → only the targeted agent acts → the agent's BuildAgentInformation
 * status flips through {@code MAINTENANCE} → admin UI sees the transition over the WebSocket subscription.
 *
 * A regression that accidentally short-circuits any of those hops (for example a future refactor that calls the
 * service directly instead of publishing to the topic) would still pass on the single-node fast pipeline but fail
 * here, because the only way to reach the build-agent-only node from a core REST endpoint is via the Hazelcast
 * broadcast.
 *
 * Tagged @multi-node so the single-node fast pipeline skips it; only the multi-node runner executes this file.
 */

interface BuildAgentDTO {
    name: string;
    memberAddress: string;
    displayName: string;
}

interface BuildAgentInformation {
    buildAgent: BuildAgentDTO;
    status: 'ACTIVE' | 'IDLE' | 'PAUSED' | 'SELF_PAUSED' | 'MAINTENANCE';
}

const AGENT_LIST_TIMEOUT_MS = 60_000;
const STATUS_TRANSITION_TIMEOUT_MS = 60_000;

test.describe('Build agent maintenance actions', { tag: '@multi-node' }, () => {
    test.beforeEach('Login as admin', async ({ page }) => {
        await Commands.login(page, admin);
    });

    /**
     * Fetches the list of build agents from the API and returns the first non-paused one. Skipping paused agents
     * keeps the test idempotent against state left over from prior runs (an admin pause from another test would
     * otherwise produce ambiguous MAINTENANCE/PAUSED assertions).
     */
    async function pickRunningAgent(page: import('@playwright/test').Page): Promise<BuildAgentDTO> {
        const result = await expect
            .poll(
                async () => {
                    const response = await page.request.get('/api/core/admin/build-agents');
                    if (!response.ok()) {
                        return undefined;
                    }
                    const agents = (await response.json()) as BuildAgentInformation[];
                    return agents.find((a) => a.status === 'ACTIVE' || a.status === 'IDLE');
                },
                { timeout: AGENT_LIST_TIMEOUT_MS, message: 'No build agent reached an ACTIVE/IDLE state' },
            )
            .toBeDefined();
        // expect.poll returns void; re-fetch to get the value back out.
        const response = await page.request.get('/api/core/admin/build-agents');
        const agents = (await response.json()) as BuildAgentInformation[];
        const target = agents.find((a) => a.status === 'ACTIVE' || a.status === 'IDLE');
        if (!target) {
            throw new Error('No running build agent available for the maintenance test');
        }
        return target.buildAgent;
        // (the `result` assertion above just gates the wait; the actual return uses the latest fetch)
    }

    async function currentAgentStatus(page: import('@playwright/test').Page, agentName: string): Promise<string | undefined> {
        const response = await page.request.get('/api/core/admin/build-agents');
        if (!response.ok()) {
            return undefined;
        }
        const agents = (await response.json()) as BuildAgentInformation[];
        return agents.find((a) => a.buildAgent.name === agentName)?.status;
    }

    test('Run cache cleanup flips the agent through MAINTENANCE and back', async ({ page }) => {
        const agent = await pickRunningAgent(page);

        await page.goto(`/admin/build-agents/details?agentName=${encodeURIComponent(agent.name)}`);
        await page.waitForLoadState('load');

        // The disk-usage tile is part of the new admin UI; assert it renders.
        await expect(page.getByTestId('disk-usage-tile')).toBeVisible();
        await expect(page.getByTestId('disk-total')).toBeVisible();

        await page.getByTestId('run-cache-cleanup-button').click();

        // Status must briefly transition to MAINTENANCE. This is observed via the REST API rather than the UI
        // text because the UI badge translation is volatile across locales; the API status enum is stable.
        await expect
            .poll(async () => currentAgentStatus(page, agent.name), { timeout: STATUS_TRANSITION_TIMEOUT_MS, message: 'Agent never entered MAINTENANCE status' })
            .toBe('MAINTENANCE');

        // Then it must return to a running state (IDLE if no jobs, ACTIVE if jobs got queued in the meantime).
        await expect
            .poll(async () => currentAgentStatus(page, agent.name), { timeout: STATUS_TRANSITION_TIMEOUT_MS, message: 'Agent never returned to IDLE/ACTIVE after cleanup' })
            .toMatch(/^(IDLE|ACTIVE)$/);
    });

    test('Reclaim disk dialog: type-CONFIRM gate and multi-option dispatch', async ({ page }) => {
        const agent = await pickRunningAgent(page);

        await page.goto(`/admin/build-agents/details?agentName=${encodeURIComponent(agent.name)}`);
        await page.waitForLoadState('load');

        await page.getByTestId('reclaim-disk-button').click();

        // Dialog visible.
        const confirmButton = page.getByTestId('reclaim-confirm-button');
        const confirmInput = page.getByTestId('reclaim-confirm-input');
        await expect(confirmButton).toBeVisible();
        await expect(confirmButton).toBeDisabled();

        // Selecting an option alone does not enable Confirm — the type-CONFIRM gate must still fail.
        await page.getByTestId('reclaim-maven-checkbox').check();
        await expect(confirmButton).toBeDisabled();

        // Wrong confirm word still keeps Confirm disabled.
        await confirmInput.fill('reclaim'); // lowercase, doesn't match
        await expect(confirmButton).toBeDisabled();

        // Correct confirm word + at least one option → enabled.
        await confirmInput.fill('RECLAIM');
        await expect(confirmButton).toBeEnabled();

        // Also select Gradle so the click dispatches two REST calls; keep Docker unchecked.
        await page.getByTestId('reclaim-gradle-checkbox').check();

        await confirmButton.click();

        // Status flips through MAINTENANCE again — either Maven or Gradle wipe acquired the pause first.
        await expect
            .poll(async () => currentAgentStatus(page, agent.name), { timeout: STATUS_TRANSITION_TIMEOUT_MS, message: 'Agent never entered MAINTENANCE during the wipe' })
            .toBe('MAINTENANCE');
        await expect
            .poll(async () => currentAgentStatus(page, agent.name), { timeout: STATUS_TRANSITION_TIMEOUT_MS, message: 'Agent never returned to IDLE/ACTIVE after the wipe' })
            .toMatch(/^(IDLE|ACTIVE)$/);
    });

    test('Cancel from the Reclaim disk dialog dispatches no REST call', async ({ page }) => {
        const agent = await pickRunningAgent(page);

        await page.goto(`/admin/build-agents/details?agentName=${encodeURIComponent(agent.name)}`);
        await page.waitForLoadState('load');

        // Intercept the wipe endpoints and fail the test if any of them fires.
        let requestObserved = false;
        await page.route('**/api/core/admin/agents/*/cache/**', async (route) => {
            requestObserved = true;
            await route.fulfill({ status: 204 });
        });
        await page.route('**/api/core/admin/agents/*/docker-images', async (route) => {
            requestObserved = true;
            await route.fulfill({ status: 204 });
        });

        await page.getByTestId('reclaim-disk-button').click();
        await page.getByTestId('reclaim-maven-checkbox').check();
        await page.getByTestId('reclaim-cancel-button').click();

        // Give the network a beat to ensure no late-fired request slips by.
        await page.waitForTimeout(500);
        expect(requestObserved).toBe(false);
    });
});
