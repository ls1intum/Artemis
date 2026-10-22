import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { CourseBrowserData, IndexedContentObject, IndexedEntityRecord } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

import { IndexOverview } from 'app/openapi/model/index-overview';
import { IngestionCoverage } from 'app/openapi/model/ingestion-coverage';

describe('CourseIngestionDashboardService', () => {
    let service: CourseIngestionDashboardService;
    let httpMock: HttpTestingController;

    const baseUrl = 'api/global-search/admin';

    const mockOverview: IndexOverview = {
        weaviateReachable: true,
        weaviateAddress: 'http://weaviate:8080',
        irisEnabled: true,
        irisReachable: true,
        collections: [
            { collection: 'ArtemisSearchableEntity', count: 42, readable: true },
            { collection: 'Lectures', count: 0, readable: true },
            { collection: 'LectureUnits', readable: false },
        ],
    };

    const mockCoverage: IngestionCoverage[] = [
        {
            courseId: 1,
            courseTitle: 'Algorithms',
            releaseDate: '2026-01-01T00:00:00Z',
            active: true,
            semester: 'WS26',
            status: 'INCOMPLETE',
            coverageGapScore: 7,
            computedAt: '2026-08-05T10:00:00Z',
            lastIngestedAt: '2026-08-04T09:00:00Z',
            typeCounts: [{ type: 'exercise', expected: 10, indexed: 3, missing: 7, orphaned: 0 }],
        },
    ];

    const mockBrowserData: CourseBrowserData = {
        entities: [{ type: 'lecture_unit', entityId: 21, title: 'Unit', lectureId: 20, expected: true }],
        contentPresence: [{ key: 'slides', unitIds: [21] }],
        missingEntities: [],
        contentGaps: [],
        typeCounts: [{ type: 'lecture_unit', expected: 1, indexed: 1, missing: 0, orphaned: 0 }],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should GET the index overview', async () => {
        const overview$ = service.getIndexOverview();
        httpMock.expectNone('/api/global-search/admin/index/overview');
        const resultPromise = firstValueFrom(overview$);

        const req = httpMock.expectOne('/api/global-search/admin/index/overview');
        expect(req.request.method).toBe('GET');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toBeNull();
        expect(req.request.responseType).toBe('json');
        req.flush(mockOverview);

        expect(await resultPromise).toEqual(mockOverview);
        httpMock.verify();
    });

    it('should preserve false flags and omitted unavailable counts on a fresh read', async () => {
        const unavailable: IndexOverview = { ...mockOverview, weaviateReachable: false, irisEnabled: false, irisReachable: false };
        const overview$ = service.getIndexOverview();
        const first = firstValueFrom(overview$);
        httpMock.expectOne('/api/global-search/admin/index/overview').flush(mockOverview);
        await expect(first).resolves.toEqual(mockOverview);

        const second = firstValueFrom(overview$);
        httpMock.expectOne('/api/global-search/admin/index/overview').flush(unavailable);
        await expect(second).resolves.toEqual(unavailable);
        expect((await second).collections[2]).not.toHaveProperty('count');
        httpMock.verify();
    });

    it('should propagate an overview HTTP failure and allow an explicit read to recover', async () => {
        const failed = firstValueFrom(service.getIndexOverview());
        const rejection = expect(failed).rejects.toMatchObject({ status: 503 });
        httpMock.expectOne('/api/global-search/admin/index/overview').flush({ title: 'Unavailable' }, { status: 503, statusText: 'Service Unavailable' });
        await rejection;
        httpMock.expectNone('/api/global-search/admin/index/overview');

        const recovered = firstValueFrom(service.getIndexOverview());
        httpMock.expectOne('/api/global-search/admin/index/overview').flush(mockOverview);
        await expect(recovered).resolves.toEqual(mockOverview);
        httpMock.verify();
    });

    it('should cancel a pending overview request on unsubscribe', () => {
        const subscription = service.getIndexOverview().subscribe();
        const req = httpMock.expectOne('/api/global-search/admin/index/overview');
        expect(req.cancelled).toBe(false);
        subscription.unsubscribe();
        expect(req.cancelled).toBe(true);
        httpMock.verify();
    });

    it('should GET stored coverage with status, search and pagination params and read X-Total-Count', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage({ status: 'INCOMPLETE', search: 'algo', page: 0, size: 20, sort: 'coverageGapScore,desc' }));

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('status')).toBe('INCOMPLETE');
        // The stored path takes the search too, so filtering and searching narrow the same rows.
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('search')).toBe('algo');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('page')).toBe('0');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('size')).toBe('20');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('sort')).toBe('coverageGapScore,desc');
        req.flush(mockCoverage, { headers: { 'X-Total-Count': '57' } });

        const result = await resultPromise;
        expect(result.content).toEqual(mockCoverage);
        expect(result.totalElements).toBe(57);
        httpMock.verify();
    });

    it('should GET stored coverage without optional params when none are provided', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage());

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('status')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('active')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('search')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('page')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('size')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('sort')).toBe(false);
        req.flush([]);

        const result = await resultPromise;
        expect(result.content).toEqual([]);
        expect(result.totalElements).toBe(0);
        httpMock.verify();
    });

    it('should GET stored coverage with the active filter param', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage({ active: false, page: 0, size: 20 }));

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('active')).toBe('false');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('status')).toBe(false);
        req.flush([]);

        await resultPromise;
        httpMock.verify();
    });

    it('should GET the live coverage page with search and read X-Total-Count', async () => {
        const resultPromise = firstValueFrom(service.getLiveCoveragePage({ search: 'algo', page: 1, size: 10, sort: 'title,asc' }));

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage/page` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('search')).toBe('algo');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('page')).toBe('1');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('size')).toBe('10');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('sort')).toBe('title,asc');
        req.flush(mockCoverage, { headers: { 'X-Total-Count': '1' } });

        const result = await resultPromise;
        expect(result.content).toEqual(mockCoverage);
        expect(result.totalElements).toBe(1);
        httpMock.verify();
    });

    it('should default missing stored coverage response fields to an empty page', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage({ sort: '' }));

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('sort')).toBe(false);
        req.flush(null);

        await expect(resultPromise).resolves.toEqual({ content: [], totalElements: 0 });
        httpMock.verify();
    });

    it('should not send an empty search param on the live coverage page', async () => {
        const resultPromise = firstValueFrom(service.getLiveCoveragePage({ search: '', page: 0, size: 20 }));

        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/coverage/page` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.has('search')).toBe(false);
        expect(new URL(req.request.urlWithParams, 'https://artemis.test').searchParams.get('page')).toBe('0');
        req.flush([]);

        await resultPromise;
        httpMock.verify();
    });

    describe.each([
        { path: '/api/global-search/admin/coverage', load: () => service.getStoredCoverage() },
        { path: '/api/global-search/admin/coverage/page', load: () => service.getLiveCoveragePage() },
    ])('$path generated transport', ({ path, load }) => {
        it('is cold, preserves JSON arrays and uses the total header independently of page length', async () => {
            const page$ = load();
            httpMock.expectNone((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
            const result = firstValueFrom(page$);
            const req = httpMock.expectOne(path);
            expect(req.request.method).toBe('GET');
            expect(req.request.body).toBeNull();
            expect(req.request.responseType).toBe('json');
            expect(new URL(req.request.urlWithParams, 'https://artemis.test').search).toBe('');
            req.flush(mockCoverage, { headers: { 'X-Total-Count': '57' } });
            await expect(result).resolves.toEqual({ content: mockCoverage, totalElements: 57 });
            httpMock.verify();
        });

        it.each([
            { body: null, headers: undefined, content: [], total: 0 },
            { body: [], headers: { 'X-Total-Count': '0' }, content: [], total: 0 },
            { body: mockCoverage, headers: { 'X-Total-Count': '0' }, content: mockCoverage, total: 0 },
            { body: mockCoverage, headers: undefined, content: mockCoverage, total: 0 },
            { body: null, headers: { 'X-Total-Count': '12' }, content: [], total: 12 },
        ])('preserves successful body/header fallbacks: $total', async ({ body, headers, content, total }) => {
            const result = firstValueFrom(load());
            httpMock.expectOne(path).flush(body, { headers });
            await expect(result).resolves.toEqual({ content, totalElements: total });
            httpMock.verify();
        });

        it.each([400, 401, 403, 503])('propagates %s without retry and recovers on an explicit read', async (status) => {
            const result = firstValueFrom(load());
            const rejected = expect(result).rejects.toMatchObject({ status });
            httpMock.expectOne(path).flush(null, { status, statusText: 'Failed' });
            await rejected;
            httpMock.expectNone(path);
            const recovered = firstValueFrom(load());
            httpMock.expectOne(path).flush(mockCoverage, { headers: { 'X-Total-Count': '1' } });
            await expect(recovered).resolves.toEqual({ content: mockCoverage, totalElements: 1 });
            httpMock.verify();
        });

        it('cancels pending HTTP on unsubscription', () => {
            const subscription = load().subscribe();
            const req = httpMock.expectOne(path);
            expect(req.cancelled).toBe(false);
            subscription.unsubscribe();
            expect(req.cancelled).toBe(true);
            httpMock.verify();
        });
    });

    it.each(['COMPLETE', 'INCOMPLETE', 'EMPTY'] as const)('serializes stored status %s, page zero and inactive false', async (status) => {
        const result = firstValueFrom(service.getStoredCoverage({ status, active: false, page: 0, size: 20, sort: 'courseTitle,asc' }));
        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === '/api/global-search/admin/coverage');
        const params = new URL(req.request.urlWithParams, 'https://artemis.test').searchParams;
        expect([...params.keys()].sort()).toEqual(['active', 'page', 'size', 'sort', 'status']);
        expect(params.get('status')).toBe(status);
        expect(params.get('active')).toBe('false');
        expect(params.get('page')).toBe('0');
        expect(params.getAll('sort')).toEqual(['courseTitle,asc']);
        req.flush([]);
        await result;
        httpMock.verify();
    });

    it.each(['stored', 'live'] as const)('preserves special-character searches and a single Spring sort on %s', async (mode) => {
        const search = '  C++ & 50%_\\ Überprüfung 東京  ';
        const request = { search, page: 0, sort: 'title,desc' };
        const result = firstValueFrom(mode === 'stored' ? service.getStoredCoverage(request) : service.getLiveCoveragePage(request));
        const path = mode === 'stored' ? '/api/global-search/admin/coverage' : '/api/global-search/admin/coverage/page';
        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
        const params = new URL(req.request.urlWithParams, 'https://artemis.test').searchParams;
        expect([...params.keys()].sort()).toEqual(['page', 'search', 'sort']);
        expect(params.get('search')).toBe(search);
        expect(params.get('page')).toBe('0');
        expect(params.getAll('sort')).toEqual(['title,desc']);
        req.flush([]);
        await result;
        httpMock.verify();
    });

    it.each(['stored', 'live'] as const)('omits empty sort and search without inventing pagination on %s', async (mode) => {
        const request = { sort: '', search: '' };
        const result = firstValueFrom(mode === 'stored' ? service.getStoredCoverage(request) : service.getLiveCoveragePage(request));
        httpMock.expectOne(mode === 'stored' ? '/api/global-search/admin/coverage' : '/api/global-search/admin/coverage/page').flush([]);
        await expect(result).resolves.toEqual({ content: [], totalElements: 0 });
        httpMock.verify();
    });

    it('should POST to force a coverage refresh', async () => {
        const resultPromise = firstValueFrom(service.refreshCoverage());

        const req = httpMock.expectOne(`${baseUrl}/coverage/refresh`);
        expect(req.request.method).toBe('POST');
        req.flush(null);

        await resultPromise;
        httpMock.verify();
    });

    it('should surface a failed coverage refresh to the dashboard error UI', async () => {
        const resultPromise = firstValueFrom(service.refreshCoverage());

        const req = httpMock.expectOne(`${baseUrl}/coverage/refresh`);
        req.flush({ title: 'Coverage unavailable' }, { status: 503, statusText: 'Service Unavailable' });

        await expect(resultPromise).rejects.toMatchObject({ status: 503 });
        httpMock.verify();
    });

    it('should GET the complete browser payload for the requested course', async () => {
        const resultPromise = firstValueFrom(service.getCourseBrowserData(1));

        const req = httpMock.expectOne(`${baseUrl}/courses/1/browser`);
        expect(req.request.method).toBe('GET');
        req.flush(mockBrowserData);

        await expect(resultPromise).resolves.toEqual(mockBrowserData);
        httpMock.verify();
    });

    it('should request indexed records and unit content with their selected fields', async () => {
        const recordsPromise = firstValueFrom(service.getIndexedEntityRecords(1, 'lecture_unit'));
        const recordsRequest = httpMock.expectOne((r) => r.url === `${baseUrl}/courses/1/entities` && r.method === 'GET');
        expect(recordsRequest.request.params.get('type')).toBe('lecture_unit');
        const records: IndexedEntityRecord[] = [{ type: 'lecture_unit', entityId: 21, title: 'Unit', properties: { course_id: 1 } }];
        recordsRequest.flush(records);

        const contentPromise = firstValueFrom(service.getUnitContent(1, 21, 'slides'));
        const contentRequest = httpMock.expectOne((r) => r.url === `${baseUrl}/courses/1/units/21/content` && r.method === 'GET');
        expect(contentRequest.request.params.get('key')).toBe('slides');
        const content: IndexedContentObject[] = [{ ingestedAt: '2026-08-05T10:00:00Z', properties: { unit_id: 21 } }];
        contentRequest.flush(content);

        await expect(recordsPromise).resolves.toEqual(records);
        await expect(contentPromise).resolves.toEqual(content);
        httpMock.verify();
    });
});
