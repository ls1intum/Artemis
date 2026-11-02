import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { IrisDashboardService } from './iris-dashboard.service';
import { IrisAdminDashboardApi } from 'app/openapi/api/iris-admin-dashboard-api';
import { IrisDashboardOverview } from 'app/openapi/model/iris-dashboard-overview';
import { IrisDashboardConfig } from 'app/openapi/model/iris-dashboard-config';
import { IrisDashboardBreakdownEntry } from 'app/openapi/model/iris-dashboard-breakdown-entry';
import { IrisDashboardTimeSeries, IrisDashboardTimeSeriesMetricEnumValues } from 'app/openapi/model/iris-dashboard-time-series';

const baseUrl = '/api/iris/admin/dashboard/';
const from = '2026-10-29T12:34:56.789Z';
const to = '2026-10-30T12:34:56.789Z';
const zeroOverview: IrisDashboardOverview = {
    totalSessions: 0,
    activeSessions: 0,
    engagementRate: 0,
    totalMessages: 0,
    uniqueUsers: 0,
    noResponseRate: 0,
    noResponseMessageCount: 0,
    noResponseSessionCount: 0,
    thumbsUpRatio: 0,
    thumbsDownRatio: 0,
    thumbsUpAbsoluteRate: 0,
    thumbsDownAbsoluteRate: 0,
    sessionsWithThumbsUp: 0,
    sessionsWithThumbsDown: 0,
    thumbsUpCount: 0,
    thumbsDownCount: 0,
    avgResponseTimeSeconds: 0,
    p50ResponseTimeSeconds: 0,
    p95ResponseTimeSeconds: 0,
    totalTokenCostEur: 0,
};

describe('Iris dashboard generated HTTP boundary', () => {
    let service: IrisDashboardService;
    let api: IrisAdminDashboardApi;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(IrisDashboardService);
        api = TestBed.inject(IrisAdminDashboardApi);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    function expectRead(endpoint: string, query: Record<string, string>): TestRequest {
        const request = http.expectOne((candidate) => new URL(candidate.urlWithParams, 'http://localhost').pathname === baseUrl + endpoint);
        expect(request.request.method).toBe('GET');
        expect(request.request.responseType).toBe('json');
        const params = new URL(request.request.urlWithParams, 'http://localhost').searchParams;
        expect(Object.fromEntries(params)).toEqual(query);
        expect([...params]).toHaveLength(Object.keys(query).length);
        return request;
    }

    it.each(['ALL', 'CLOUD', 'LOCAL'] as const)('delegates a cold overview read with explicit %s and exact UTC instants', (aiSelection) => {
        const result = service.getOverview(from, to, aiSelection);
        http.expectNone((request) => request.url.startsWith(baseUrl));
        let actual: IrisDashboardOverview | undefined;
        result.subscribe((value) => (actual = value));
        const request = expectRead('overview', { from, to, aiSelection });
        expect(request.request.url).toContain('12%3A34%3A56.789Z');
        request.flush(zeroOverview);
        expect(actual).toEqual(zeroOverview);
    });

    it.each(['CHAT_MODE', 'COURSE', 'MODEL'] as const)('delegates %s without losing map keys, zeroes or optional labels', (dimension) => {
        const entries: IrisDashboardBreakdownEntry[] = [
            { label: 'example', values: { sessions: 1, activeSessions: 0, messages: 2, noResponseRate: 0, totalTokens: 42, costEur: 0, extraMetric: 3.25 } },
            { values: { sessions: 0 } },
        ];
        let actual: IrisDashboardBreakdownEntry[] | undefined;
        service.getBreakdown(from, to, dimension, 'LOCAL').subscribe((value) => (actual = value));
        expectRead('breakdown', { from, to, dimension, aiSelection: 'LOCAL' }).flush(entries);
        expect(actual).toEqual(entries);
    });

    it('preserves empty breakdown arrays', () => {
        let actual: IrisDashboardBreakdownEntry[] | undefined;
        service.getBreakdown(from, to, 'COURSE', 'ALL').subscribe((value) => (actual = value));
        expectRead('breakdown', { from, to, dimension: 'COURSE', aiSelection: 'ALL' }).flush([]);
        expect(actual).toEqual([]);
    });

    it('keeps the Observable config method and parses false/zero and an omitted cron', () => {
        const config: IrisDashboardConfig = {
            maxQueryWindowDays: 90,
            staleThresholdMinutes: 5,
            digestEnabled: false,
            alertEnabled: false,
            alertNoResponseRateThreshold: 0,
            alertCheckIntervalMinutes: 0,
            alertCooldownMinutes: 0,
            alertLookbackMinutes: 0,
            alertMinimumEligibleSessions: 0,
            alertMinimumUserMessages: 0,
        };
        let actual: IrisDashboardConfig | undefined;
        api.getConfig().subscribe((value) => (actual = value));
        expectRead('config', {}).flush(config);
        expect(actual).toEqual(config);
    });

    it.each(IrisDashboardTimeSeriesMetricEnumValues)('generates the unused time-series endpoint for %s without a feature wrapper', (metric) => {
        const series: IrisDashboardTimeSeries = { metric, entries: [{ bucketStart: from, series: { total: 0, other: 1.25 } }] };
        let actual: IrisDashboardTimeSeries | undefined;
        api.getTimeSeries(from, to, 'YEAR', metric, 'CLOUD').subscribe((value) => (actual = value));
        expectRead('time-series', { from, to, span: 'YEAR', metric, aiSelection: 'CLOUD' }).flush(series);
        expect(actual).toEqual(series);
    });

    it('omits an unspecified AI filter so the server can apply ALL on every windowed endpoint', () => {
        api.getOverview(from, to).subscribe();
        api.getBreakdown(from, to, 'MODEL').subscribe();
        api.getTimeSeries(from, to, 'day', 'SESSIONS').subscribe();
        expectRead('overview', { from, to }).flush(zeroOverview);
        expectRead('breakdown', { from, to, dimension: 'MODEL' }).flush([]);
        expectRead('time-series', { from, to, span: 'day', metric: 'SESSIONS' }).flush({ metric: 'SESSIONS', entries: [{ bucketStart: from, series: { total: 0, active: 0 } }] });
    });

    it.each([400, 403, 500])('propagates HTTP %s from all four endpoints', (status) => {
        const failures: HttpErrorResponse[] = [];
        const observer = { error: (error: HttpErrorResponse) => failures.push(error) };
        service.getOverview(from, to, 'ALL').subscribe(observer);
        service.getBreakdown(from, to, 'MODEL', 'ALL').subscribe(observer);
        api.getTimeSeries(from, to, 'DAY', 'SESSIONS').subscribe(observer);
        api.getConfig().subscribe(observer);
        expectRead('overview', { from, to, aiSelection: 'ALL' }).flush({}, { status, statusText: 'Failure' });
        expectRead('breakdown', { from, to, dimension: 'MODEL', aiSelection: 'ALL' }).flush({}, { status, statusText: 'Failure' });
        expectRead('time-series', { from, to, span: 'DAY', metric: 'SESSIONS' }).flush({}, { status, statusText: 'Failure' });
        expectRead('config', {}).flush({}, { status, statusText: 'Failure' });
        expect(failures.map((error) => error.status)).toEqual([status, status, status, status]);
    });

    it('cancels generated overview and breakdown requests when callers unsubscribe', () => {
        const overview = service.getOverview(from, to, 'ALL').subscribe();
        const breakdown = service.getBreakdown(from, to, 'MODEL', 'ALL').subscribe();
        const overviewRequest = expectRead('overview', { from, to, aiSelection: 'ALL' });
        const breakdownRequest = expectRead('breakdown', { from, to, dimension: 'MODEL', aiSelection: 'ALL' });
        overview.unsubscribe();
        breakdown.unsubscribe();
        expect(overviewRequest.cancelled).toBe(true);
        expect(breakdownRequest.cancelled).toBe(true);
    });
});
