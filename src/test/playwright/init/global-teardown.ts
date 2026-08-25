import fs from 'fs';
import path from 'path';
import { request } from '@playwright/test';
import { admin } from '../support/users';

const REPORT_JSON_PATH = path.join(__dirname, '..', 'test-reports', 'slow-query-report.json');

/**
 * Collects the runtime slow-query report (thesis objective 4.4) while the Artemis server is
 * still reachable. CI tears the server container down the moment this Playwright container
 * exits, so any collection attempted from a later, separate CI step always hits a dead server —
 * this must happen here, as the last thing before teardown, not from the GitHub Actions workflow.
 */
async function globalTeardown() {
    console.log('Running global teardown...');
    await collectSlowQueryReport();
}

async function collectSlowQueryReport(): Promise<void> {
    // Self-hosted runners have persistent workspaces: an old report from a previous run must
    // never be mistaken for this run's data if collection below fails partway through.
    fs.rmSync(REPORT_JSON_PATH, { force: true });

    const baseURL = process.env.BASE_URL ?? 'http://localhost:9000';
    const ctx = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
    try {
        const authResponse = await ctx.post('api/core/public/authenticate', {
            data: { username: admin.username, password: admin.password, rememberMe: true },
        });
        if (!authResponse.ok()) {
            console.warn(`[slow-query] Could not authenticate admin for report collection: HTTP ${authResponse.status()}`);
            return;
        }

        const setCookieHeaders = authResponse.headers()['set-cookie'];
        const jwtMatch = setCookieHeaders?.match(/jwt=([^;]+)/);
        if (!jwtMatch) {
            console.warn('[slow-query] Could not extract admin JWT for report collection');
            return;
        }

        const reportResponse = await ctx.get('api/core/admin/performance/slow-queries', {
            headers: { cookie: `jwt=${jwtMatch[1]}` },
        });
        if (!reportResponse.ok()) {
            // Expected when the e2e-performance profile isn't active (endpoint doesn't exist / 401/403/404).
            console.warn(`[slow-query] Report endpoint returned HTTP ${reportResponse.status()} (e2e-performance profile may not be active)`);
            return;
        }

        fs.mkdirSync(path.dirname(REPORT_JSON_PATH), { recursive: true });
        fs.writeFileSync(REPORT_JSON_PATH, await reportResponse.text());
        console.log(`[slow-query] Report saved to ${REPORT_JSON_PATH}`);
    } catch (error) {
        console.warn(`[slow-query] Could not collect slow-query report: ${error}`);
    } finally {
        await ctx.dispose();
    }
}

export default globalTeardown;
