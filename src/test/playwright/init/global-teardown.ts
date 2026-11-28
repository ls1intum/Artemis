import fs from 'fs';
import path from 'path';
import { request } from '@playwright/test';
import { admin } from '../support/users';
import { QueryReport, mergeQueryReports } from './mergeQueryReports';

const REPORT_JSON_PATH = path.join(__dirname, '..', 'test-reports', 'query-report.json');

/**
 * Collects the runtime query report while the Artemis server is still reachable. CI tears the
 * server container down the moment this Playwright container exits, so any collection attempted
 * from a later, separate CI step always hits a dead server — this must happen here, as the last
 * thing before teardown, not from the GitHub Actions workflow.
 */
async function globalTeardown() {
    console.log('Running global teardown...');
    await collectQueryReport();
}

async function collectQueryReport(): Promise<void> {
    // Self-hosted runners have persistent workspaces: an old report from a previous run must
    // never be mistaken for this run's data if collection below fails partway through.
    fs.rmSync(REPORT_JSON_PATH, { force: true });

    // Every core node keeps its own in-memory report, so on a multi-node deployment each node is
    // asked directly instead of whichever one the load balancer picks.
    const nodeUrls = (process.env.MULTI_NODE_URLS ?? '')
        .split(',')
        .map((url) => url.trim())
        .filter((url) => url.length > 0);
    const baseUrls = nodeUrls.length > 0 ? nodeUrls : [process.env.BASE_URL ?? 'http://localhost:9000'];

    const reports: QueryReport[] = [];
    for (const baseURL of baseUrls) {
        const report = await fetchReport(baseURL);
        if (!report) {
            // a report missing one node's findings would make them look new in later comparisons
            console.warn(`[query-report] No report from ${baseURL}; not saving a partial report`);
            return;
        }
        reports.push(report);
    }

    const merged = mergeQueryReports(reports);
    fs.mkdirSync(path.dirname(REPORT_JSON_PATH), { recursive: true });
    fs.writeFileSync(REPORT_JSON_PATH, JSON.stringify(merged));
    console.log(`[query-report] Report of ${reports.length} node(s) saved to ${REPORT_JSON_PATH}`);
}

async function fetchReport(baseURL: string): Promise<QueryReport | undefined> {
    const ctx = await request.newContext({ baseURL, ignoreHTTPSErrors: true });
    try {
        const authResponse = await ctx.post('api/core/public/authenticate', {
            data: { username: admin.username, password: admin.password, rememberMe: true },
        });
        if (!authResponse.ok()) {
            console.warn(`[query-report] Could not authenticate admin for report collection: HTTP ${authResponse.status()}`);
            return undefined;
        }

        const setCookieHeaders = authResponse.headers()['set-cookie'];
        const jwtMatch = setCookieHeaders?.match(/jwt=([^;]+)/);
        if (!jwtMatch) {
            console.warn('[query-report] Could not extract admin JWT for report collection');
            return undefined;
        }

        const reportResponse = await ctx.get('api/admin/performance/query-report', {
            headers: { cookie: `jwt=${jwtMatch[1]}` },
        });
        if (!reportResponse.ok()) {
            // Expected when the e2e-performance profile isn't active (endpoint doesn't exist / 401/403/404).
            console.warn(`[query-report] Report endpoint returned HTTP ${reportResponse.status()} (e2e-performance profile may not be active)`);
            return undefined;
        }
        return (await reportResponse.json()) as QueryReport;
    } catch (error) {
        console.warn(`[query-report] Could not collect the query report: ${error}`);
        return undefined;
    } finally {
        await ctx.dispose();
    }
}

export default globalTeardown;
