import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';

import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';

import { IndexOverview } from 'app/openapi/model/index-overview';
import { IngestionCoverage } from 'app/openapi/model/ingestion-coverage';
import { CourseBrowserData } from 'app/openapi/model/course-browser-data';
import { IndexedEntityRecord } from 'app/openapi/model/indexed-entity-record';
import { IndexedContentObject } from 'app/openapi/model/indexed-content-object';

describe('CourseIngestionDashboardService', () => {
    let service: CourseIngestionDashboardService;
    let httpMock: HttpTestingController;

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

    describe('generated coverage refresh transport', () => {
        const path = '/api/global-search/admin/coverage/refresh';

        it('is cold and emits and completes on a genuine empty 200 without a request body', () => {
            const refresh$ = service.refreshCoverage();
            httpMock.expectNone(path);
            const next = vi.fn();
            const complete = vi.fn();
            const error = vi.fn();
            refresh$.subscribe({ next, complete, error });

            const req = httpMock.expectOne(path);
            expect(req.request.method).toBe('POST');
            expect(req.request.urlWithParams).toBe(path);
            expect(req.request.params.keys()).toEqual([]);
            expect(req.request.body).toBeNull();
            expect(req.request.headers.has('Content-Type')).toBe(false);
            expect(req.request.responseType).toBe('json');
            expect(next).not.toHaveBeenCalled();
            req.flush(null, { status: 200, statusText: 'OK' });

            // Angular delivers an empty response through next; void does not require literal undefined at runtime.
            expect(next).toHaveBeenCalledOnce();
            expect(complete).toHaveBeenCalledOnce();
            expect(error).not.toHaveBeenCalled();
            httpMock.expectNone(path);

            refresh$.subscribe({ next, complete, error });
            httpMock.expectOne(path).flush(null);
            expect(next).toHaveBeenCalledTimes(2);
            expect(complete).toHaveBeenCalledTimes(2);
            httpMock.verify();
        });

        it.each([401, 403, 503])('propagates an empty %s without retry and recovers on an explicit call', async (status) => {
            const failed = firstValueFrom(service.refreshCoverage());
            const rejection = expect(failed).rejects.toMatchObject({ status, error: null });
            httpMock.expectOne(path).flush(null, { status, statusText: 'Refresh rejected' });
            await rejection;
            httpMock.expectNone(path);

            const recovered = firstValueFrom(service.refreshCoverage());
            const req = httpMock.expectOne(path);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toBeNull();
            req.flush(null);
            await recovered;
            httpMock.verify();
        });

        it('propagates a network failure without an automatic POST retry', async () => {
            const failed = firstValueFrom(service.refreshCoverage());
            const rejection = expect(failed).rejects.toMatchObject({ status: 0 });
            httpMock.expectOne(path).error(new ProgressEvent('error'));
            await rejection;
            httpMock.expectNone(path);
            httpMock.verify();
        });

        it('cancels a pending refresh when unsubscribed', () => {
            const next = vi.fn();
            const subscription = service.refreshCoverage().subscribe(next);
            const req = httpMock.expectOne(path);
            expect(req.cancelled).toBe(false);
            subscription.unsubscribe();
            expect(req.cancelled).toBe(true);
            expect(next).not.toHaveBeenCalled();
            httpMock.verify();
        });
    });

    it('should GET the complete browser payload for the requested course', async () => {
        const resultPromise = firstValueFrom(service.getCourseBrowserData(1));

        const req = httpMock.expectOne(`/api/global-search/admin/courses/1/browser`);
        expect(req.request.method).toBe('GET');
        req.flush(mockBrowserData);

        await expect(resultPromise).resolves.toEqual(mockBrowserData);
        httpMock.verify();
    });

    it('should request indexed records and unit content with their selected fields', async () => {
        const recordsPromise = firstValueFrom(service.getIndexedEntityRecords(1, 'lecture_unit'));
        const recordsRequest = httpMock.expectOne(
            (r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/courses/1/entities` && r.method === 'GET',
        );
        expect(new URL(recordsRequest.request.urlWithParams, 'https://artemis.test').searchParams.get('type')).toBe('lecture_unit');
        const records: IndexedEntityRecord[] = [{ type: 'lecture_unit', entityId: 21, title: 'Unit', properties: { course_id: 1 } }];
        recordsRequest.flush(records);

        const contentPromise = firstValueFrom(service.getUnitContent(1, 21, 'slides'));
        const contentRequest = httpMock.expectOne(
            (r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === `/api/global-search/admin/courses/1/units/21/content` && r.method === 'GET',
        );
        expect(new URL(contentRequest.request.urlWithParams, 'https://artemis.test').searchParams.get('key')).toBe('slides');
        const content: IndexedContentObject[] = [{ ingestedAt: '2026-08-05T10:00:00Z', properties: { unit_id: 21 } }];
        contentRequest.flush(content);

        await expect(recordsPromise).resolves.toEqual(records);
        await expect(contentPromise).resolves.toEqual(content);
        httpMock.verify();
    });
    describe.each([
        {
            path: '/api/global-search/admin/courses/7/browser',
            query: '',
            load: () => service.getCourseBrowserData(7),
            body: { entities: [{ entityId: 0, expected: false }], typeCounts: [] },
        },
        {
            path: '/api/global-search/admin/courses/7/entities',
            query: 'type=lecture_unit',
            load: () => service.getIndexedEntityRecords(7, 'lecture_unit'),
            body: [{ type: 'lecture_unit', entityId: 0, properties: { zero: 0, flag: false, nested: { text: '東京' } } }],
        },
        {
            path: '/api/global-search/admin/courses/7/units/0/content',
            query: 'key=slides',
            load: () => service.getUnitContent(7, 0, 'slides'),
            body: [{ properties: { page_number: 0, flag: false } }],
        },
    ])('$path generated browser transport', ({ path, query, load, body }) => {
        it('is cold and returns the JSON body with exact query/IDs and sparse values', async () => {
            const value$ = load();
            httpMock.expectNone((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
            const result = firstValueFrom<unknown>(value$);
            const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
            const url = new URL(req.request.urlWithParams, 'https://artemis.test');
            expect(url.searchParams.toString()).toBe(query);
            expect(req.request.method).toBe('GET');
            expect(req.request.body).toBeNull();
            expect(req.request.responseType).toBe('json');
            req.flush(body);
            await expect(result).resolves.toEqual(body);
            httpMock.verify();
        });

        it.each([400, 401, 403, 404, 503])('propagates %s without retry and allows explicit recovery', async (status) => {
            const result = firstValueFrom<unknown>(load());
            const rejected = expect(result).rejects.toMatchObject({ status });
            httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path).flush(null, { status, statusText: 'Failure' });
            await rejected;
            httpMock.expectNone((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
            const recovered = firstValueFrom<unknown>(load());
            httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path).flush(body);
            await expect(recovered).resolves.toEqual(body);
            httpMock.verify();
        });

        it('cancels pending HTTP when unsubscribed', () => {
            const request$: Observable<unknown> = load();
            const subscription = request$.subscribe();
            const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === path);
            expect(req.cancelled).toBe(false);
            subscription.unsubscribe();
            expect(req.cancelled).toBe(true);
            httpMock.verify();
        });
    });

    it.each(['', 'slides', 'transcript', 'unit_summary', 'segments', 'C++ & %_\\ 東京'])('preserves the required content key %s', async (key) => {
        const result = firstValueFrom(service.getUnitContent(7, 0, key));
        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === '/api/global-search/admin/courses/7/units/0/content');
        const params = new URL(req.request.urlWithParams, 'https://artemis.test').searchParams;
        expect([...params.keys()]).toEqual(['key']);
        expect(params.get('key')).toBe(key);
        req.flush([]);
        await expect(result).resolves.toEqual([]);
        httpMock.verify();
    });

    it.each(['', 'lecture', 'lecture_unit', 'course', 'C++ & %_\\ Überprüfung'])('preserves the required entity type %s', async (type) => {
        const result = firstValueFrom(service.getIndexedEntityRecords(7, type));
        const req = httpMock.expectOne((r) => new URL(r.urlWithParams, 'https://artemis.test').pathname === '/api/global-search/admin/courses/7/entities');
        const params = new URL(req.request.urlWithParams, 'https://artemis.test').searchParams;
        expect([...params.keys()]).toEqual(['type']);
        expect(params.get('type')).toBe(type);
        req.flush([]);
        await expect(result).resolves.toEqual([]);
        httpMock.verify();
    });

    it('keeps a successful empty browser object distinct from an error', async () => {
        const result = firstValueFrom(service.getCourseBrowserData(7));
        httpMock.expectOne('/api/global-search/admin/courses/7/browser').flush({});
        await expect(result).resolves.toEqual({});
        httpMock.verify();
    });
});
