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
    /** The report itself: every read of it is a call this suite makes, so it is the one endpoint whose count it controls. */
    const OWN_ENDPOINT = 'GET api/admin/feature-usage';

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
    test('Registers the endpoint inventory at startup and reports every catalogue feature', async () => {
        await expect(page.locator('[data-testid="kpi-used-features"]')).toBeVisible();

        const report = await overviewFromApi();
        // A real deployment has hundreds of endpoints; any small number here means the scan matched the wrong mapping.
        expect(report.endpoints.length).toBeGreaterThan(100);
        // Every catalogue feature is reported, including the ones this deployment does not offer
        expect(report.features.length).toBeGreaterThan(100);
        // Every endpoint is classified, and almost all of them belong to a feature of the catalogue
        const catalogue = new Set(report.features.map((feature: any) => feature.feature));
        const interactions = new Set(['ACTION', 'VIEW', 'AUTOMATIC', 'SYSTEM']);
        expect(report.endpoints.every((endpoint: any) => interactions.has(endpoint.interaction))).toBeTruthy();
        expect(report.endpoints.filter((endpoint: any) => !endpoint.retired && !catalogue.has(endpoint.featureLabel))).toHaveLength(0);

        // The headline counts features, and what is offered here has to add up
        const offered = await detailNumberOf('kpi-used-features');
        expect(offered).toBe(report.availableFeatures);
        expect(report.usedFeatures + report.onlyAutomatic + report.unusedFeatures).toBe(report.availableFeatures);
    });

    /**
     * The status probes a page sends on its own must not count as use. The account lookup runs on every page load, so it
     * is the one automatic endpoint this test can rely on having been called.
     */
    test('Classifies automatic calls separately from use', async () => {
        const report = await overviewFromApi();
        const account = report.endpoints.find((endpoint: any) => endpoint.identifier === 'GET api/core/public/account');

        expect(account?.interaction).toBe('AUTOMATIC');
        expect(report.endpoints.find((endpoint: any) => endpoint.identifier === OWN_ENDPOINT)?.interaction).toBe('VIEW');
    });

    test('Lists the features that need attention and counts them like the headline', async () => {
        await expect(page.locator('[data-testid="kpi-unused-features"]')).toBeVisible();

        await page.locator('[data-testid="tab-attention"]').click();
        await expect(page.locator('[data-testid="attention-unused"]')).toBeVisible();

        // The headline and the list have to agree: they used to count different things, so the page read
        // "895 unused" above a list of 131 rows.
        expect(await countOf('attention-unused')).toBe(await headlineNumberOf('kpi-unused-features'));
        expect(await countOf('attention-onlyAutomatic')).toBe(await headlineNumberOf('kpi-only-automatic'));
    });

    /**
     * The whole write path in one assertion: this browser's own API traffic has to appear in the database and come back
     * through the read API. Polling rather than a fixed wait, because the flush is scheduled and the test must not depend
     * on landing between two ticks.
     */
    test('Records the calls of a request that just happened', async () => {
        // Asserted on this endpoint's own count, and as an increase over what is already stored. A headline total is not
        // enough: tests running in parallel raise it too, so it can grow while the calls made here are still in memory.
        // And a stack that has served other tests first already has counts, against which "more than zero" passes
        // without the flush ever having run. Every poll reads the report, so each one is itself a call to this endpoint.
        const before = await callsOfEndpoint(OWN_ENDPOINT);

        await expect.poll(() => callsOfEndpoint(OWN_ENDPOINT), { timeout: 90000, intervals: [5000] }).toBeGreaterThan(before);

        // The caller's role bucket is the only thing recorded about who called, so it has to be resolved and stored.
        await reloadPage();
        await expect(page.locator('[data-testid="role-distribution"]')).toContainText('ADMIN');
    });

    /**
     * Requesting the admin API is itself a tracked feature, so its own row is the one call this test can attribute
     * exactly. It also proves the catalogue survives the round trip: the call arrives under the feature, in its product
     * area, served by the controller that handles it.
     */
    test('Attributes the calls to the feature and the resource that served them', async () => {
        // Waits for this feature itself rather than relying on the previous test: that one proves a flush for one endpoint,
        // and this one must hold when it runs alone as well.
        await expect
            .poll(async () => (await overviewFromApi()).features.find((feature: any) => feature.feature === 'FEATURE_USAGE')?.status, { timeout: 90000, intervals: [5000] })
            .toBe('USED');
        await reloadPage();

        await page.locator('[data-testid="tab-features"]').click();
        await page.locator('[data-testid="search-input"]').fill(OWN_ENDPOINT.slice(OWN_ENDPOINT.indexOf(' ') + 1));

        await page.locator('[data-testid="toggle-ADMINISTRATION"]').click();
        const ownFeature = page.locator('[data-testid="tree-row-ADMINISTRATION/FEATURE_USAGE"]');
        await expect(ownFeature).toHaveAttribute('data-status', 'USED');

        await page.locator('[data-testid="toggle-ADMINISTRATION/FEATURE_USAGE"]').click();
        await expect(page.locator('[data-kind="resource"]', { hasText: 'AdminFeatureUsageResource' })).toBeVisible();
    });

    async function reloadPage(): Promise<void> {
        await page.reload();
        await page.waitForLoadState('domcontentloaded');
        await dismissPasskeyReminderIfPresent(page);
    }

    async function callsOfEndpoint(identifier: string): Promise<number> {
        const report = await overviewFromApi();
        return report.endpoints.find((endpoint: any) => endpoint.identifier === identifier)?.callCount ?? 0;
    }

    /**
     * Addressed by test id rather than by position or by label text: the card component owns wrapper elements the test
     * has no business counting, and every label on this page is translated, so matching on text would pass in English
     * and fail in German.
     */
    async function headlineNumberOf(testId: string): Promise<number> {
        return Number((await page.locator(`[data-testid="${testId}"] [data-testid="kpi-value"]`).innerText()).replace(/[^0-9]/g, ''));
    }

    async function detailNumberOf(testId: string): Promise<number> {
        return Number((await page.locator(`[data-testid="${testId}"] [data-testid="kpi-detail"]`).innerText()).replace(/[^0-9]/g, ''));
    }

    async function countOf(sectionTestId: string): Promise<number> {
        return Number((await page.locator(`[data-testid="${sectionTestId}"] [data-testid="attention-count"]`).innerText()).replace(/[^0-9]/g, ''));
    }

    async function overviewFromApi(): Promise<any> {
        const response = await page.request.get('/api/admin/feature-usage?days=30');
        expect(response.ok()).toBeTruthy();
        const report = await response.json();
        return { ...report, features: report.features ?? [], endpoints: report.endpoints ?? [] };
    }
});
