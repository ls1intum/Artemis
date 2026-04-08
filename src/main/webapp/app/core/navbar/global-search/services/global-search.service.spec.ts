import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GlobalSearchResult, GlobalSearchService } from './global-search.service';

describe('GlobalSearchService', () => {
    setupTestBed({ zoneless: true });

    let service: GlobalSearchService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), GlobalSearchService],
        });
        service = TestBed.inject(GlobalSearchService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should perform basic search', () => {
        const mockResults: GlobalSearchResult[] = [
            {
                id: '1',
                type: 'exercise',
                title: 'Test Exercise',
                description: 'A test exercise',
                badge: 'Programming',
                metadata: { points: 100 },
            },
        ];

        service.search('test').subscribe((results) => {
            expect(results).toEqual(mockResults);
        });

        const req = httpMock.expectOne((request) => request.url === 'api/search' && request.params.get('q') === 'test');
        expect(req.request.method).toBe('GET');
        req.flush(mockResults);
    });

    it('should include type filter when provided', () => {
        service.search('test', { type: 'exercise' }).subscribe();

        const req = httpMock.expectOne((request) => request.url === 'api/search' && request.params.get('type') === 'exercise');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should include courseId when provided', () => {
        service.search('test', { courseId: 123 }).subscribe();

        const req = httpMock.expectOne((request) => request.url === 'api/search' && request.params.get('courseId') === '123');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should include limit when provided', () => {
        service.search('test', { limit: 20 }).subscribe();

        const req = httpMock.expectOne((request) => request.url === 'api/search' && request.params.get('limit') === '20');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should include all options when type, courseId, and limit are provided', () => {
        service.search('q', { type: 'exercise', courseId: 123, limit: 10 }).subscribe();

        const req = httpMock.expectOne(
            (request) =>
                request.url === 'api/search' &&
                request.params.get('q') === 'q' &&
                request.params.get('type') === 'exercise' &&
                request.params.get('courseId') === '123' &&
                request.params.get('limit') === '10',
        );
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should propagate network errors', () => {
        service.search('test').subscribe({
            next: () => {
                throw new Error('expected an error');
            },
            error: (err) => expect(err).toBeTruthy(),
        });

        const req = httpMock.expectOne((request) => request.url === 'api/search' && request.params.get('q') === 'test');
        req.error(new ProgressEvent('error'));
    });
});
