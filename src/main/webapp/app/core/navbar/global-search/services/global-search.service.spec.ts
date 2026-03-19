import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { GlobalSearchResult, GlobalSearchService } from './global-search.service';
import { provideHttpClient } from '@angular/common/http';

describe('GlobalSearchService', () => {
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
});
