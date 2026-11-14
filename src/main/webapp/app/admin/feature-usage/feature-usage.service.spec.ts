import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';

import type { FeatureAdoption } from 'app/openapi/model/feature-adoption';
import type { FeatureUsageOverview } from 'app/openapi/model/feature-usage-overview';
import type { FeatureUsageTrendPoint } from 'app/openapi/model/feature-usage-trend-point';
import { FeatureUsageService } from './feature-usage.service';
import { FeatureInteraction, FeatureKind, FeatureUsageStatus } from './feature-usage.model';

const OVERVIEW_PATH = '/api/admin/feature-usage';
const TREND_PATH = OVERVIEW_PATH + '/trend';
const DIGEST_PATH = OVERVIEW_PATH + '/digest/send-email';
const TEST_ORIGIN = 'https://artemis.example';

const emptyOverview: FeatureUsageOverview = {
    days: 7,
    from: '2026-09-18',
    availableFeatures: 0,
    usedFeatures: 0,
    onlyAutomatic: 0,
    unusedFeatures: 0,
    notAvailable: 0,
    noActions: 0,
    retiredEndpoints: 0,
    actionCount: 0,
    viewCount: 0,
    automaticCount: 0,
    systemCount: 0,
    inventoryRefreshedAt: '2026-09-18T09:10:11Z',
};

const trend: FeatureUsageTrendPoint[] = [{ usageDay: '2026-09-24', interaction: 'ACTION', callCount: 2 }];

const adoption: FeatureAdoption[] = [
    { module: 'programming', key: 'static-code-analysis', feature: 'PROGRAMMING_GRADING_CONFIGURATION', count: 0, total: 3 },
    { module: 'course', key: 'some-course-setting', count: 0, total: 0 },
];

describe('FeatureUsageService', () => {
    let service: FeatureUsageService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(FeatureUsageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    function expectRequest(method: string, path: string) {
        const request = httpMock.expectOne((candidate) => candidate.method === method && new URL(candidate.urlWithParams, TEST_ORIGIN).pathname === path);
        return { request, url: new URL(request.request.urlWithParams, TEST_ORIGIN) };
    }

    it.each([undefined, ''])('should omit an unselected caller role (%s)', (callerRole) => {
        const next = vi.fn();
        service.getOverview(7, callerRole).subscribe(next);

        const { request, url } = expectRequest('GET', OVERVIEW_PATH);
        expect(url.origin).toBe(TEST_ORIGIN);
        expect([...url.searchParams.entries()]).toEqual([['days', '7']]);
        request.flush(emptyOverview);
        expect(next.mock.calls).toStrictEqual([[emptyOverview]]);
    });

    it('should preserve overview enums, dates, role distribution and historical labels', () => {
        const response: FeatureUsageOverview = {
            ...emptyOverview,
            callerRole: 'STUDENT',
            recordingSince: '2026-09-18T11:10:11+02:00',
            features: [
                {
                    feature: 'FAQ',
                    area: 'COMMUNICATION',
                    status: 'UNUSED',
                    noActions: false,
                    actionCount: 0,
                    viewCount: 0,
                    automaticCount: 0,
                    systemCount: 0,
                    errorCount: 0,
                    durationSumMs: 0,
                    durationMaxMs: 0,
                    activeDays: 0,
                    actionDays: 0,
                    endpointCount: 1,
                    hasActionEndpoints: false,
                },
            ],
            endpoints: [
                {
                    featureId: 1,
                    featureKind: 'REST',
                    module: 'communication',
                    identifier: 'GET api/communication/old-faqs',
                    featureLabel: 'historical/old-faqs',
                    interaction: 'VIEW',
                    callCount: 0,
                    errorCount: 0,
                    durationSumMs: 0,
                    durationMaxMs: 0,
                    activeDays: 0,
                    lastRegisteredAt: '2026-09-18T09:10:11Z',
                    retired: false,
                },
            ],
            roleDistribution: [{ callerRole: 'TEACHING_ASSISTANT', callCount: 2 }],
        };
        const next = vi.fn();
        service.getOverview(7, 'STUDENT').subscribe(next);

        const { request, url } = expectRequest('GET', OVERVIEW_PATH);
        expect(url.searchParams.get('callerRole')).toBe('STUDENT');
        request.flush(response);
        expect(next).toHaveBeenCalledExactlyOnceWith({
            ...response,
            features: response.features?.map((feature) => ({ ...feature, status: FeatureUsageStatus.UNUSED })),
            endpoints: response.endpoints?.map((endpoint) => ({ ...endpoint, featureKind: FeatureKind.REST, interaction: FeatureInteraction.VIEW })),
        });
    });

    it('should chart a feature by its catalogue name', () => {
        const next = vi.fn();
        service.getFeatureTrend('FAQ', 90, 'EDITOR').subscribe(next);

        const { request, url } = expectRequest('GET', TREND_PATH);
        expect(url.searchParams.get('feature')).toBe('FAQ');
        expect(url.searchParams.get('days')).toBe('90');
        expect(url.searchParams.get('callerRole')).toBe('EDITOR');
        expect(url.searchParams.has('featureIds')).toBe(false);
        request.flush(trend);
        expect(next).toHaveBeenCalledExactlyOnceWith([{ usageDay: '2026-09-24', interaction: FeatureInteraction.ACTION, callCount: 2 }]);
    });

    it.each([undefined, '', 'TEACHING_ASSISTANT'])('should chart endpoints by repeating their ids with caller role %s', (callerRole) => {
        const next = vi.fn();
        service.getEndpointTrend([1, 2], 7, callerRole).subscribe(next);

        const { request, url } = expectRequest('GET', TREND_PATH);
        expect(url.search).toContain('featureIds=1&featureIds=2');
        expect(url.searchParams.getAll('featureIds')).toEqual(['1', '2']);
        expect(url.searchParams.get('days')).toBe('7');
        expect(url.searchParams.has('feature')).toBe(false);
        expect(url.searchParams.get('callerRole')).toBe(callerRole || null);
        request.flush([]);
        expect(next).toHaveBeenCalledExactlyOnceWith([]);
    });

    it('should forward an invalid window and propagate the server error', () => {
        const error = vi.fn();
        const problem = { errorKey: 'invalidWindow' };
        service.getOverview(5).subscribe({ error });

        const { request, url } = expectRequest('GET', OVERVIEW_PATH);
        expect(url.searchParams.get('days')).toBe('5');
        request.flush(problem, { status: 400, statusText: 'Bad Request' });
        expect(error).toHaveBeenCalledExactlyOnceWith(expect.any(HttpErrorResponse));
        expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 400, error: problem }));
    });

    it('should leave validation of an empty endpoint selector to the server', () => {
        const error = vi.fn();
        const problem = { errorKey: 'invalidTrendSelector' };
        service.getEndpointTrend([], 30).subscribe({ error });

        const { request, url } = expectRequest('GET', TREND_PATH);
        expect([...url.searchParams.entries()]).toEqual([['days', '30']]);
        request.flush(problem, { status: 400, statusText: 'Bad Request' });
        expect(error).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ status: 400, error: problem }));
    });

    it('should reject an unsupported caller role without issuing an unfiltered request', () => {
        const error = vi.fn();
        service.getOverview(7, 'USER').subscribe({ error });
        service.getEndpointTrend([1], 7, 'USER').subscribe({ error });

        httpMock.expectNone(() => true);
        expect(error.mock.calls).toEqual([[new Error('Unsupported feature usage caller role: USER')], [new Error('Unsupported feature usage caller role: USER')]]);
    });

    it('should reject an unsupported feature without issuing a trend request', () => {
        const error = vi.fn();
        service.getFeatureTrend('faq', 7).subscribe({ error });

        httpMock.expectNone(() => true);
        expect(error).toHaveBeenCalledExactlyOnceWith(new Error('Unsupported catalogue feature: faq'));
    });

    it.each([{ response: adoption }, { response: [] }])('should emit the adoption array', ({ response }) => {
        const next = vi.fn();
        service.getAdoption().subscribe(next);

        const { request, url } = expectRequest('GET', OVERVIEW_PATH + '/adoption');
        expect(url.search).toBe('');
        request.flush(response);
        expect(next).toHaveBeenCalledExactlyOnceWith(response);
    });

    it('should send a bodyless POST and emit on an empty 200', () => {
        const next = vi.fn();
        service.sendDigestEmail().subscribe(next);

        const { request } = expectRequest('POST', DIGEST_PATH);
        expect(request.request.body).toBeNull();
        request.flush(null, { status: 200, statusText: 'OK' });
        expect(next).toHaveBeenCalledExactlyOnceWith(undefined);
    });

    it('should propagate an empty digest 400 as an HTTP error', () => {
        const error = vi.fn();
        service.sendDigestEmail().subscribe({ error });

        const { request } = expectRequest('POST', DIGEST_PATH);
        expect(request.request.body).toBeNull();
        request.flush(null, { status: 400, statusText: 'Bad Request' });
        expect(error).toHaveBeenCalledExactlyOnceWith(expect.any(HttpErrorResponse));
        expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 400, error: null }));
    });
});
