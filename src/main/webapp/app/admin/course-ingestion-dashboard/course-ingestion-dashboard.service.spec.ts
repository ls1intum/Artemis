import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview, IngestionCoverage } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionDashboardService', () => {
    let service: CourseIngestionDashboardService;
    let httpMock: HttpTestingController;

    const baseUrl = 'api/global-search/admin';

    const mockOverview: IndexOverview = {
        weaviateReachable: true,
        weaviateAddress: 'http://weaviate:8080',
        irisEnabled: true,
        collections: [
            { collection: 'ArtemisSearchableEntity', count: 42, readable: true },
            { collection: 'LectureUnits', count: null, readable: false },
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should GET the index overview', async () => {
        const resultPromise = firstValueFrom(service.getIndexOverview());

        const req = httpMock.expectOne(`${baseUrl}/index/overview`);
        expect(req.request.method).toBe('GET');
        req.flush(mockOverview);

        expect(await resultPromise).toEqual(mockOverview);
        httpMock.verify();
    });

    it('should GET stored coverage with status and pagination params and read X-Total-Count', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage({ status: 'INCOMPLETE', page: 0, size: 20, sort: 'coverageGapScore,desc' }));

        const req = httpMock.expectOne((r) => r.url === `${baseUrl}/coverage` && r.method === 'GET');
        expect(req.request.params.get('status')).toBe('INCOMPLETE');
        expect(req.request.params.get('page')).toBe('0');
        expect(req.request.params.get('size')).toBe('20');
        expect(req.request.params.get('sort')).toBe('coverageGapScore,desc');
        req.flush(mockCoverage, { headers: { 'X-Total-Count': '57' } });

        const result = await resultPromise;
        expect(result.content).toEqual(mockCoverage);
        expect(result.totalElements).toBe(57);
        httpMock.verify();
    });

    it('should GET stored coverage without optional params when none are provided', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage());

        const req = httpMock.expectOne((r) => r.url === `${baseUrl}/coverage` && r.method === 'GET');
        expect(req.request.params.has('status')).toBe(false);
        expect(req.request.params.has('active')).toBe(false);
        expect(req.request.params.has('page')).toBe(false);
        expect(req.request.params.has('size')).toBe(false);
        expect(req.request.params.has('sort')).toBe(false);
        req.flush([]);

        const result = await resultPromise;
        expect(result.content).toEqual([]);
        expect(result.totalElements).toBe(0);
        httpMock.verify();
    });

    it('should GET stored coverage with the active filter param', async () => {
        const resultPromise = firstValueFrom(service.getStoredCoverage({ active: false, page: 0, size: 20 }));

        const req = httpMock.expectOne((r) => r.url === `${baseUrl}/coverage` && r.method === 'GET');
        expect(req.request.params.get('active')).toBe('false');
        expect(req.request.params.has('status')).toBe(false);
        req.flush([]);

        await resultPromise;
        httpMock.verify();
    });

    it('should GET the live coverage page with search and read X-Total-Count', async () => {
        const resultPromise = firstValueFrom(service.getLiveCoveragePage({ search: 'algo', page: 1, size: 10, sort: 'title,asc' }));

        const req = httpMock.expectOne((r) => r.url === `${baseUrl}/coverage/page` && r.method === 'GET');
        expect(req.request.params.get('search')).toBe('algo');
        expect(req.request.params.get('page')).toBe('1');
        expect(req.request.params.get('size')).toBe('10');
        expect(req.request.params.get('sort')).toBe('title,asc');
        req.flush(mockCoverage, { headers: { 'X-Total-Count': '1' } });

        const result = await resultPromise;
        expect(result.content).toEqual(mockCoverage);
        expect(result.totalElements).toBe(1);
        httpMock.verify();
    });

    it('should not send an empty search param on the live coverage page', async () => {
        const resultPromise = firstValueFrom(service.getLiveCoveragePage({ search: '', page: 0, size: 20 }));

        const req = httpMock.expectOne((r) => r.url === `${baseUrl}/coverage/page` && r.method === 'GET');
        expect(req.request.params.has('search')).toBe(false);
        expect(req.request.params.get('page')).toBe('0');
        req.flush([]);

        await resultPromise;
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
});
