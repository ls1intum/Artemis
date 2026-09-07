import { Page, expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Commands } from '../../support/commands';
import { dismissPasskeyReminderIfPresent } from '../../support/dismissPasskeyReminder';

/**
 * End-to-end coverage of the built-in feature usage analysis.
 *
 * <p>
 * The unit tests cover each stage in isolation, but the whole point of this feature is a chain: an interceptor resolves
 * the handler method, a collector accumulates in memory, a scheduled flush writes an additive delta, and an aggregate
 * query reads it back. Every link can be correct on its own while the chain reports nothing, and that is not a
 * hypothetical failure mode — it is what actually happened once already, when the startup scan resolved Spring's handler
 * mapping by type and silently matched Actuator's second bean, so the inventory stayed empty and no call was ever
 * recorded. Nothing below the page would have noticed.
 *
 * These tests therefore assert on the two things that only a running server can show: that the inventory was written
 * from the real mapping table at startup, and that a request made now reaches the database and comes back out of the
 * read API.
 *
 * The suite cannot wait out the production five-minute flush interval, so `playwright.env` shortens it for the E2E
 * stack. Everything else runs with the shipped defaults.
 */
test.describe('Feature usage analysis', { tag: '@fast' }, () => {
    let page: Page;

    test.beforeAll('Login as admin', async ({ browser }) => {
        page = await browser.newPage();
        await Commands.login(page, admin, '/admin/feature-usage');
        await page.waitForLoadState('domcontentloaded');
        // The passkey setup reminder is a CDK overlay with a backdrop that swallows every click on the page behind it.
        // Its absence locally and presence on the E2E stack, where passkeys are enabled, is what made this look like
        // flakiness: the elements resolve and report visible and enabled, and only the click is intercepted.
        await dismissPasskeyReminderIfPresent(page);
    });

    /**
     * The inventory is written from Spring's own mapping table for every endpoint, whether or not anyone ever called it.
     * That is what lets the page answer "what can we delete" rather than only "what is popular", so an empty inventory
     * makes the whole feature useless while looking perfectly healthy.
     */
    test('Registers the endpoint inventory at startup', async () => {
        await expect(page.locator('[data-testid="kpi-tracked-features"]')).toBeVisible();

        // A real deployment has hundreds of endpoints; any small number here means the scan matched the wrong mapping.
        const endpoints = await endpointCountOf('kpi-tracked-features');
        expect(endpoints).toBeGreaterThan(100);

        // Features, not endpoints, is what the headline leads with and what the tables below it list.
        const features = await headlineNumberOf('kpi-tracked-features');
        expect(features).toBeGreaterThan(0);
        expect(features).toBeLessThanOrEqual(endpoints);
    });

    test('Lists unused features and counts them in features rather than endpoints', async () => {
        await expect(page.locator('[data-testid="kpi-unused-features"]')).toBeVisible();

        await page.locator('[data-testid="tab-unused"]').click();
        const rows = page.locator('table tbody tr');
        await expect.poll(() => rows.count(), { timeout: 10000 }).toBeGreaterThan(0);

        // The headline and the table have to agree: they used to count different things, so the page read
        // "895 unused" above a list of 131 rows.
        expect(await rows.count()).toBe(await headlineNumberOf('kpi-unused-features'));
    });

    /**
     * The whole write path in one assertion: this browser's own API traffic has to appear in the database and come back
     * through the read API. Polling with reloads rather than a fixed wait, because the flush is scheduled and the test
     * must not depend on landing between two ticks.
     */
    test('Records the calls of a request that just happened', async () => {
        // Asserted as an increase over what is already stored, not as "more than zero". A stack that has served other
        // tests first already has counts, and against those a zero-based assertion passes instantly without the flush
        // ever having run - proving nothing while looking like coverage.
        const before = await headlineNumberOf('kpi-total-calls');

        await expect
            .poll(
                async () => {
                    await page.reload();
                    await page.waitForLoadState('domcontentloaded');
                    await dismissPasskeyReminderIfPresent(page);
                    return headlineNumberOf('kpi-total-calls');
                },
                { timeout: 90000, intervals: [5000] },
            )
            .toBeGreaterThan(before);

        // The caller's role bucket is the only thing recorded about who called, so it has to be resolved and stored.
        await expect(page.locator('[data-testid="role-distribution"]')).toContainText('ADMIN');
    });

    /**
     * Requesting the admin API is itself a tracked feature, so its own row is the one call this test can attribute
     * exactly. It also proves the label taxonomy survives the round trip rather than the row appearing by raw path.
     */
    test('Attributes the calls to the labelled feature that served them', async () => {
        await page.locator('[data-testid="tab-all-features"]').click();
        const ownRow = page.locator('table tbody tr', { hasText: 'monitoring/feature-usage' });

        await expect.poll(() => ownRow.count(), { timeout: 10000 }).toBeGreaterThan(0);
        await expect(ownRow.first()).toContainText('admin');
    });

    /**
     * Addressed by test id rather than by position or by label text: the card component owns wrapper elements the test
     * has no business counting, and every label on this page is translated, so matching on text would pass in English
     * and fail in German.
     */
    async function headlineNumberOf(testId: string): Promise<number> {
        return Number((await page.locator(`[data-testid="${testId}"] [data-testid="kpi-value"]`).innerText()).replace(/[^0-9]/g, ''));
    }

    async function endpointCountOf(testId: string): Promise<number> {
        return Number((await page.locator(`[data-testid="${testId}"] [data-testid="kpi-endpoints"]`).innerText()).replace(/[^0-9]/g, ''));
    }
});
