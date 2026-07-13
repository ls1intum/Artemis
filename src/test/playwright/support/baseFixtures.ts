import { Browser, CDPSession, Page, test as baseTest, expect } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';
import fs from 'fs';
import path from 'path';
import { SEED_COURSES } from './seedData';
import { addE2EInitScript, storeCapturedApiResponseBody } from './utils';

/**
 * Lazy-loaded Angular routes that e2e tests commonly hit. Pre-warming these on each
 * Playwright worker downloads the route chunks into Chromium's per-worker disk cache,
 * so subsequent test navigations don't race the chunk fetch (which under heavy
 * multi-node load occasionally fails and drops the user on `/courses`).
 *
 * The list deliberately picks ONE representative URL per distinct lazy module: leaf
 * components like course-detail, course-update, iris-settings etc. are loaded
 * transitively when their parent shell renders. Adding more routes here yields
 * diminishing returns past ~10 entries.
 */
const PREWARM_ROUTES = [
    '/course-management',
    `/course-management/${SEED_COURSES.atlas1.id}`,
    `/course-management/${SEED_COURSES.atlas1.id}/competency-management`,
    `/course-management/${SEED_COURSES.atlas1.id}/learning-path-management`,
    `/course-management/${SEED_COURSES.examManagement.id}/exams`,
    `/course-management/${SEED_COURSES.lectureManagement.id}/lectures`,
    `/courses`,
    `/courses/${SEED_COURSES.general.id}`,
    '/admin/user-management',
];

const JWT_TOKENS_PATH = path.join(__dirname, '..', '.auth', 'jwt-tokens.json');

let chunksWarmedOnThisWorker = false;

/**
 * Read the admin JWT cached by `init/global-setup.ts`. Used by the chunk-warm fixture
 * to skip the auth round-trip — the JWT cookie is injected directly into the warm-up
 * browser context.
 */
function readAdminJwt(): string | undefined {
    try {
        const raw = fs.readFileSync(JWT_TOKENS_PATH, 'utf-8');
        const tokens = JSON.parse(raw) as Record<string, { value: string; expires: number }>;
        return tokens['artemis_admin']?.value;
    } catch {
        return undefined;
    }
}

/**
 * Visit each lazy-loaded route once with an authenticated browser context so Chromium
 * caches the JS chunks on disk. Subsequent tests in the same worker hit the cache
 * instead of refetching, eliminating the chunk-fetch race under load.
 *
 * Best-effort: every navigation is `.catch`ed so a single slow route never breaks the
 * worker. Runs only once per worker (gated by the module-level `chunksWarmedOnThisWorker`
 * flag).
 */
async function prewarmChunks(browser: Browser): Promise<void> {
    if (chunksWarmedOnThisWorker) return;
    chunksWarmedOnThisWorker = true;

    const baseURL = process.env.BASE_URL ?? 'http://localhost:9000';
    const jwt = readAdminJwt();
    if (!jwt) {
        console.log('[prewarm] skipping chunk warm — admin JWT not available');
        return;
    }

    const ctx = await browser.newContext({ ignoreHTTPSErrors: true });
    try {
        const url = new URL(baseURL);
        await ctx.addCookies([
            {
                name: 'jwt',
                value: jwt,
                domain: url.hostname,
                path: '/',
                httpOnly: false,
                secure: url.protocol === 'https:',
                sameSite: 'Lax',
            },
        ]);
        const page = await ctx.newPage();
        const start = Date.now();
        for (const route of PREWARM_ROUTES) {
            const t0 = Date.now();
            await page
                .goto(baseURL + route, { waitUntil: 'domcontentloaded', timeout: 30_000 })
                .then(() => console.log(`[prewarm]   ${(Date.now() - t0).toString().padStart(5)} ms  ${route}`))
                .catch((error) => console.log(`[prewarm]   skip ${route}: ${error.message ?? error}`));
        }
        console.log(`[prewarm] worker chunk warm finished in ${Date.now() - start} ms`);
    } finally {
        await ctx.close();
    }
}

/**
 * A trimmed, diverse subset of {@link PREWARM_ROUTES} used by the per-test in-context warm-up
 * ({@link warmChunksInContext}). Four routes cover the heaviest, most distinct lazy modules the
 * failing tests hit (course-management shell, atlas competency/learning-path, exam management,
 * student course view); keeping it small bounds the per-test warm-up cost.
 */
const WARM_ROUTES = [
    `/course-management/${SEED_COURSES.atlas1.id}`,
    `/course-management/${SEED_COURSES.atlas1.id}/learning-path-management`,
    `/course-management/${SEED_COURSES.examManagement.id}/exams`,
    `/courses/${SEED_COURSES.general.id}`,
];

/**
 * EXPERIMENT (getResponseBody flake): warm the JS/CSS chunks into the CURRENT test's own browser
 * context, so the test's real navigations serve them from cache instead of re-downloading. Fewer
 * large chunk responses churning Chromium's per-renderer network buffer means the small `/api`
 * response bodies tests read via `waitForResponse().json()` are far less likely to be evicted before
 * the read ("Network.getResponseBody: No data found for resource").
 *
 * Why in-context and not the worker-scoped {@link prewarmChunks}: Playwright gives each test a fresh,
 * non-persistent (incognito) context whose HTTP cache is per-context and in-memory — it does NOT
 * share the browser's on-disk cache. So the worker prewarm, which warms a throwaway context that is
 * then closed, cannot populate the caches the tests actually use. Warming inside the test's own
 * context is what makes the cache hit land where it matters.
 *
 * Isolation-safe: the suite uses no `storageState`, so the context is clean (logged out) when this
 * runs. We borrow the cached admin JWT only to reach the authenticated lazy routes, then clear all
 * cookies + origin storage so the test starts from the same clean state it would have otherwise.
 * Best-effort throughout. Gated to CI (where the flake manifests at worker scale); disable with
 * PW_WARM_CACHE=off.
 */
let warmCacheStatusLogged = false;

async function warmChunksInContext(page: Page): Promise<void> {
    const jwt = readAdminJwt();
    if (!jwt) {
        if (!warmCacheStatusLogged) {
            warmCacheStatusLogged = true;
            console.log('[warm-cache] skipped — admin JWT not available');
        }
        return;
    }
    if (!warmCacheStatusLogged) {
        warmCacheStatusLogged = true;
        console.log(`[warm-cache] in-context chunk warm-up ACTIVE (${WARM_ROUTES.length} routes per test)`);
    }

    const baseURL = process.env.BASE_URL ?? 'http://localhost:9000';
    const url = new URL(baseURL);
    const ctx = page.context();

    await ctx.addCookies([
        {
            name: 'jwt',
            value: jwt,
            domain: url.hostname,
            path: '/',
            httpOnly: false,
            secure: url.protocol === 'https:',
            sameSite: 'Lax',
        },
    ]);

    for (const route of WARM_ROUTES) {
        await page.goto(baseURL + route, { waitUntil: 'domcontentloaded', timeout: 30_000 }).catch(() => {});
    }

    // Reset to the clean, logged-out state the test expects (it performs its own login/setup).
    await ctx.clearCookies().catch(() => {});
    await page
        .evaluate(() => {
            try {
                localStorage.clear();
                sessionStorage.clear();
            } catch {
                /* storage may be inaccessible on some origins — ignore */
            }
        })
        .catch(() => {});
    await page.goto('about:blank').catch(() => {});
}

const test = baseTest.extend<
    {
        autoTestFixture: string;
        virtualAuthenticator: CDPSession;
    },
    { chunksWarmed: void }
>({
    /**
     * Worker-scoped auto fixture: visits the lazy-loaded routes once per Playwright
     * worker so Chromium caches the JS chunks on disk before any test runs. Workers
     * run in parallel, so the wall-clock cost equals one worker's run, not the sum.
     */
    chunksWarmed: [
        async ({ browser }, use) => {
            await prewarmChunks(browser);
            await use();
        },
        { scope: 'worker', auto: true },
    ],
    autoTestFixture: [
        async ({ page }: { page: Page }, use: (fixture: string) => Promise<void>) => {
            // Add shared init scripts that suppress overlays (notification popup, passkey modal)
            // which would block test interactions. See addE2EInitScript for details.
            await addE2EInitScript(page);

            // Enlarge Chrome's per-renderer network resource buffer (default ~10 MB) so response bodies are
            // not evicted before a test reads them. Under Angular 22 + parallel E2E load, cold-context JS-chunk
            // re-fetches (large responses) churn the default buffer fast enough to drop the small API response
            // bodies, causing intermittent "Network.getResponseBody: No data found for resource" on
            // page.waitForResponse(...).json(). maxTotalBufferSize/maxResourceBufferSize are renderer-level, so
            // enlarging them here keeps those bodies retrievable. Best-effort.
            try {
                const cdpSession = await page.context().newCDPSession(page);
                await cdpSession.send('Network.enable', {
                    maxTotalBufferSize: 256 * 1024 * 1024,
                    maxResourceBufferSize: 128 * 1024 * 1024,
                });
            } catch {
                // Ignore — if the CDP command is unavailable the eager-buffer handler below still helps.
            }

            // Eagerly buffer API response bodies as they arrive. Playwright caches a response body the
            // first time it is read, so a later `response.json()` in a test returns that cached copy instead
            // of issuing a fresh CDP `Network.getResponseBody` call. Under the Angular 22 runtime + parallel
            // load, that late CDP read intermittently returns "No data found for resource with given
            // identifier" because the browser has already evicted the body from its per-page network buffer
            // by the time the test reads it (the request itself succeeded server-side). Reading the body the
            // moment the response event fires — while it is still buffered — makes those reads deterministic.
            // Best-effort (`.catch`) and scoped to `/api/` responses to avoid buffering large static assets.
            page.on('response', (response) => {
                if (response.url().includes('/api/')) {
                    void response.body().catch(() => {});
                }
            });

            // Capture non-GET API response bodies at the network layer so tests can read them even when
            // Chrome evicts the body from its per-renderer buffer before the read (the getResponseBody
            // flake). page.route().fetch() issues the request from Node and holds the body in Node memory —
            // eviction-proof, and unlike the read-side replay in readResponseJson it works for POST/PUT/DELETE
            // (whose responses must not be replayed). GETs stay on the normal path: they are already
            // recoverable via replay, and skipping them avoids buffering any streaming/SSE endpoint (all GET).
            // Best-effort — any failure falls back to route.continue() (the prior behaviour). CI-only;
            // PW_API_CAPTURE=off disables it.
            if (process.env.CI && process.env.PW_API_CAPTURE !== 'off') {
                await page.route('**/api/**', async (route) => {
                    if (route.request().method() === 'GET') {
                        await route.continue();
                        return;
                    }
                    try {
                        const apiResponse = await route.fetch();
                        storeCapturedApiResponseBody(page, route.request(), await apiResponse.body());
                        await route.fulfill({ response: apiResponse });
                    } catch {
                        await route.continue().catch(() => {});
                    }
                });
            }

            // EXPERIMENT: warm the app's JS/CSS chunks into this test's context before it runs, so the
            // test's navigations hit the cache and don't churn the network buffer that holds the /api
            // response bodies. Only in CI (where the getResponseBody flake manifests); PW_WARM_CACHE=off
            // disables it. Runs before coverage start so its navigations are not counted.
            if (process.env.CI && process.env.PW_WARM_CACHE !== 'off') {
                await warmChunksInContext(page).catch(() => {});
            }

            const coverageEnabled = process.env.PLAYWRIGHT_COVERAGE !== 'off';

            if (coverageEnabled) {
                await page.coverage.startJSCoverage({
                    resetOnNavigation: false,
                });
            }

            await use('autoTestFixture');

            if (coverageEnabled) {
                const jsCoverage = await page.coverage.stopJSCoverage();

                if (jsCoverage && jsCoverage.length > 0) {
                    // On CI, modify URLs of coverage entries to access sources
                    // directly from the "artemis-app" container.
                    // Because files served via "nginx" require HTTPS certificates
                    // for accessing them, and it's not clear how we can make
                    // "monocart-reporter" handle that while generating a coverage report.
                    if (process.env.CI) {
                        for (const entry of jsCoverage) {
                            entry.url = entry.url.replace(process.env.BASE_URL!, 'http://artemis-app:8080');
                        }
                    }
                    await addCoverageReport(jsCoverage, test.info());
                }
            }
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
    virtualAuthenticator: async ({ page }, use) => {
        const cdpSession = await page.context().newCDPSession(page);
        await cdpSession.send('WebAuthn.enable', { enableUI: false });
        await cdpSession.send('WebAuthn.addVirtualAuthenticator', {
            options: {
                protocol: 'ctap2',
                transport: 'internal',
                hasResidentKey: true,
                hasUserVerification: true,
                isUserVerified: true,
                automaticPresenceSimulation: true,
            },
        });
        await use(cdpSession);
        await cdpSession.send('WebAuthn.disable');
    },
});

export { test, expect };
