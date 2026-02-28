import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LectureSearchService } from './lecture-search.service';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

describe('LectureSearchService', () => {
    setupTestBed({ zoneless: true });

    let service: LectureSearchService;
    let httpTesting: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LectureSearchService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(LectureSearchService);
        httpTesting = TestBed.inject(HttpTestingController);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('search()', () => {
        it('should POST to api/iris/lecture-search with query and default limit of 10', () => {
            service.search('angular signals').subscribe();

            const req = httpTesting.expectOne('api/iris/lecture-search');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({ query: 'angular signals', limit: 10 });

            req.flush([]);
        });

        it('should accept a custom limit', () => {
            service.search('spring boot', 25).subscribe();

            const req = httpTesting.expectOne('api/iris/lecture-search');
            expect(req.request.body).toEqual({ query: 'spring boot', limit: 25 });

            req.flush([]);
        });

        it('should return the results from the server', () => {
            const mockResults: LectureSearchResult[] = [
                {
                    lectureUnitId: 1,
                    lectureUnitName: 'Introduction to Signals',
                    lectureUnitLink: '/courses/1/lectures/1/units/1',
                    lectureId: 1,
                    lectureName: 'Angular Basics',
                    courseId: 1,
                    courseName: 'Advanced Web Development',
                    pageNumber: 3,
                    baseUrl: 'http://localhost',
                    snippet: 'Signals are a reactive primitive...',
                },
            ];

            let actualResults: LectureSearchResult[] | undefined;
            service.search('signals').subscribe((results) => {
                actualResults = results;
            });

            const req = httpTesting.expectOne('api/iris/lecture-search');
            req.flush(mockResults);

            expect(actualResults).toEqual(mockResults);
        });
    });
});
