import { expect, type Route } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin } from '../../support/users';

const bucketStart = '2026-05-01T00:00:00Z';

const overview = {
    totalSessions: 42,
    activeSessions: 7,
    engagementRate: 64.2,
    totalMessages: 128,
    uniqueUsers: 19,
    userMessageCount: 83,
    eligibleSessions: 39,
    noResponseRate: 6.3,
    noResponseMessageCount: 5,
    noResponseSessionCount: 3,
    thumbsUpRatio: 82.4,
    thumbsDownRatio: 17.6,
    thumbsUpAbsoluteRate: 14.0,
    thumbsDownAbsoluteRate: 3.0,
    sessionsWithThumbsUp: 11,
    sessionsWithThumbsDown: 2,
    averageResponseTimeSeconds: 2.4,
    p50ResponseTimeSeconds: 1.8,
    p95ResponseTimeSeconds: 7.2,
    totalTokenCostEur: 1.2345,
};

const config = {
    maxQueryWindowDays: 365,
    staleThresholdMinutes: 5,
    digest: {
        enabled: true,
        cron: '0 0 7 * * *',
        recipients: ['admin@example.com'],
    },
    alert: {
        enabled: true,
        noResponseRateThreshold: 10,
        checkIntervalMinutes: 30,
        cooldownMinutes: 360,
        lookbackMinutes: 60,
        minimumActiveSessions: 10,
        minimumUserMessages: 20,
        recipients: ['admin@example.com'],
    },
};

function timeSeries(metric: string) {
    const seriesByMetric: Record<string, Record<string, number>> = {
        SESSIONS: {
            PROGRAMMING_EXERCISE_CHAT: 18,
            COURSE_CHAT: 12,
        },
        MESSAGES: {
            User: 83,
            Iris: 45,
        },
        NO_RESPONSE_RATE: {
            NO_RESPONSE_RATE: 6.3,
        },
        RESPONSE_TIME: {
            AVERAGE: 2.4,
            P95: 7.2,
        },
        RATINGS: {
            THUMBS_UP: 11,
            THUMBS_DOWN: 2,
        },
        TOKEN_COST: {
            CHAT_ATTRIBUTED: 1.12,
            OTHER_IRIS: 0.11,
        },
        ENGAGEMENT: {
            ENGAGEMENT_RATE: 64.2,
        },
    };
    return {
        metric,
        entries: [
            {
                bucketStart,
                series: seriesByMetric[metric] ?? {},
            },
        ],
    };
}

function breakdown(dimension: string) {
    if (dimension === 'COURSE') {
        return [
            {
                name: 'Test Course',
                metrics: {
                    sessions: 20,
                    messages: 77,
                    noResponseRate: 5.5,
                    costEur: 0.42,
                },
            },
        ];
    }
    if (dimension === 'MODEL') {
        return [
            {
                name: 'gpt-5-mini',
                metrics: {
                    inputTokens: 12500,
                    outputTokens: 6400,
                    totalTokens: 18900,
                    costEur: 1.2345,
                },
            },
        ];
    }
    return [
        {
            name: 'PROGRAMMING_EXERCISE_CHAT',
            metrics: {
                sessions: 18,
                messages: 64,
                noResponseRate: 6.3,
                thumbsUpRatio: 82.4,
                thumbsDownRatio: 17.6,
                averageResponseTimeSeconds: 2.4,
                costEur: 0.41,
            },
        },
    ];
}

async function fulfillJson(route: Route, body: object) {
    await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
    });
}

test.describe('Iris admin dashboard', { tag: '@fast' }, () => {
    test.beforeEach(async ({ page }) => {
        await page.route('**/management/info', async (route) => {
            const response = await route.fetch();
            const info = await response.json();
            await route.fulfill({
                response,
                contentType: 'application/json',
                body: JSON.stringify({
                    ...info,
                    activeModuleFeatures: Array.from(new Set([...(info.activeModuleFeatures ?? []), 'iris'])),
                }),
            });
        });

        await page.route('**/api/iris/admin/dashboard/overview**', async (route) => {
            await fulfillJson(route, overview);
        });
        await page.route('**/api/iris/admin/dashboard/config**', async (route) => {
            await fulfillJson(route, config);
        });
        await page.route('**/api/iris/admin/dashboard/time-series**', async (route) => {
            const metric = new URL(route.request().url()).searchParams.get('metric') ?? 'SESSIONS';
            await fulfillJson(route, timeSeries(metric));
        });
        await page.route('**/api/iris/admin/dashboard/breakdown**', async (route) => {
            const dimension = new URL(route.request().url()).searchParams.get('dimension') ?? 'CHAT_MODE';
            await fulfillJson(route, breakdown(dimension));
        });
    });

    test('renders KPI, chart, table, and configuration data', async ({ page, login }) => {
        const pageErrors: string[] = [];
        page.on('pageerror', (error) => pageErrors.push(error.message));

        await login(admin, '/admin/iris-dashboard');

        await expect(page.locator('[data-cy="irisDashboardHeading"]')).toHaveText('Iris Dashboard');
        await expect(page.locator('#admin-iris-dashboard')).toBeVisible();

        const kpiSummary = page.getByLabel('Iris dashboard KPI summary');
        await expect(kpiSummary.locator('.kpi-card').filter({ hasText: 'Total Sessions' }).getByText('42', { exact: true })).toBeVisible();
        await expect(kpiSummary.locator('.kpi-card').filter({ hasText: 'No-Response Rate' }).getByText('6.3%', { exact: true })).toBeVisible();
        await expect(kpiSummary.locator('.kpi-card').filter({ hasText: 'Token Cost' }).getByText('EUR 1.2345', { exact: true })).toBeVisible();

        await expect(page.locator('.chart-panel')).toHaveCount(7);
        await expect(page.locator('.chart-panel svg').first()).toBeVisible();
        const firstChartBox = await page.locator('.chart-panel').first().boundingBox();
        expect(firstChartBox?.width).toBeGreaterThan(300);
        expect(firstChartBox?.height).toBeGreaterThan(200);

        await expect(page.getByRole('cell', { name: 'Programming' })).toBeVisible();
        await expect(page.getByRole('cell', { name: '18' })).toBeVisible();

        await page.getByRole('tab', { name: 'Course' }).click();
        await expect(page.getByRole('cell', { name: 'Test Course' })).toBeVisible();

        await page.getByRole('tab', { name: 'Model' }).click();
        await expect(page.getByRole('cell', { name: 'gpt-5-mini' })).toBeVisible();
        await expect(page.getByRole('cell', { name: '18,900' })).toBeVisible();

        await expect(page.getByText('Max Query Window')).toBeVisible();
        await expect(page.getByText('365 days')).toBeVisible();
        await expect(page.getByText('Alert Threshold')).toBeVisible();

        expect(pageErrors).toEqual([]);
    });
});
