import { Browser, CDPSession, Page, test as baseTest, expect } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';
import fs from 'fs';
import path from 'path';
import { SEED_COURSES } from './seedData';

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
            // Hide the notification popup overlay that can block clicks in e2e tests.
            // This runs before every page load to ensure the overlay never intercepts pointer events.
            await page.addInitScript(() => {
                const injectStyle = () => {
                    const style = document.createElement('style');
                    style.textContent = 'jhi-course-notification-popup-overlay { display: none !important; }';
                    document.head.appendChild(style);
                };
                if (document.head) {
                    injectStyle();
                } else {
                    document.addEventListener('DOMContentLoaded', injectStyle);
                }

                // Suppress the passkey setup modal for all tests by setting a far-future reminder date.
                // Tests that explicitly test passkey registration should clear this via
                // page.evaluate(() => localStorage.removeItem('earliestSetupPasskeyReminderDate'))
                // after navigation but before login.
                try {
                    const futureDate = new Date();
                    futureDate.setFullYear(futureDate.getFullYear() + 10);
                    localStorage.setItem('earliestSetupPasskeyReminderDate', JSON.stringify(futureDate));
                } catch {
                    // localStorage may not be available on about:blank — safe to ignore
                }
            });

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
