import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject } from 'rxjs';
import { LectureSearchService } from './lecture-search.service';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { WebsocketService } from 'app/shared/service/websocket.service';

const MOCK_RUN_ID = '550e8400-e29b-41d4-a716-446655440000';

describe('LectureSearchService', () => {
    setupTestBed({ zoneless: true });

    let service: LectureSearchService;
    let httpTesting: HttpTestingController;
    let wsSubject: Subject<IrisSearchStatusUpdate>;

    const mockWebsocketService = {
        subscribe: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
        vi.spyOn(window.crypto, 'randomUUID').mockReturnValue(MOCK_RUN_ID as `${string}-${string}-${string}-${string}-${string}`);
        wsSubject = new Subject<IrisSearchStatusUpdate>();
        mockWebsocketService.subscribe.mockReturnValue(wsSubject.asObservable());

        TestBed.configureTestingModule({
            providers: [LectureSearchService, provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: mockWebsocketService }],
        });
        service = TestBed.inject(LectureSearchService);
        httpTesting = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpTesting.verify();
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
                    course: { id: 1, name: 'Advanced Web Development' },
                    lecture: { id: 1, name: 'Angular Basics' },
                    lectureUnit: {
                        id: 1,
                        name: 'Introduction to Signals',
                        link: '/courses/1/lectures/1/units/1',
                        pageNumber: 3,
                        sourceType: 'lecture_unit_slide',
                        queryParams: { unit: 1, page: 3 },
                    },
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

    describe('ask()', () => {
        it('should POST to api/iris/search-answer with query and default limit of 5', () => {
            service.ask('what are signals?').subscribe();

            const req = httpTesting.expectOne('api/iris/search-answer');
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({ query: 'what are signals?', limit: 5, runId: MOCK_RUN_ID });

            req.flush(null, { status: 202, statusText: 'Accepted' });
        });

        it('should accept a custom limit', () => {
            service.ask('explain dependency injection', 3).subscribe();

            const req = httpTesting.expectOne('api/iris/search-answer');
            expect(req.request.body).toEqual({ query: 'explain dependency injection', limit: 3, runId: MOCK_RUN_ID });

            req.flush(null, { status: 202, statusText: 'Accepted' });
        });

        it('should emit a thinking update before the final result', () => {
            const received: IrisSearchStatusUpdate[] = [];
            service.ask('what are signals?').subscribe((result) => received.push(result));

            const req = httpTesting.expectOne('api/iris/search-answer');
            req.flush(null, { status: 202, statusText: 'Accepted' });

            wsSubject.next({ runId: MOCK_RUN_ID, isThinking: true });
            wsSubject.next({ runId: MOCK_RUN_ID, isThinking: false, answer: 'Signals are reactive.', sources: [] });

            expect(received).toHaveLength(2);
            expect(received[0].isThinking).toBe(true);
            expect(received[1].isThinking).toBe(false);
        });

        it('should return the answer and sources from the server via WebSocket', () => {
            const mockResult: IrisSearchStatusUpdate = {
                runId: MOCK_RUN_ID,
                isThinking: false,
                answer: 'Signals are a reactive primitive in Angular...',
                sources: [
                    {
                        course: { id: 1, name: 'Advanced Web Development' },
                        lecture: { id: 1, name: 'Angular Basics' },
                        lectureUnit: {
                            id: 1,
                            name: 'Introduction to Signals',
                            link: '/courses/1/lectures/1/units/1',
                            pageNumber: 3,
                            sourceType: 'lecture_unit_slide',
                            queryParams: { unit: 1, page: 3 },
                        },
                        snippet: 'Signals are a reactive primitive...',
                    },
                ],
            };

            let actualResult: IrisSearchStatusUpdate | undefined;
            service.ask('what are signals?').subscribe((result) => {
                actualResult = result;
            });

            // Flush the HTTP trigger (Artemis returns 202; result arrives via WebSocket)
            const req = httpTesting.expectOne('api/iris/search-answer');
            req.flush(null, { status: 202, statusText: 'Accepted' });

            // Simulate the WebSocket push
            wsSubject.next(mockResult);

            expect(actualResult).toEqual(mockResult);
        });

        it('should error if the WebSocket does not respond within the timeout', () => {
            vi.useFakeTimers();
            let errorReceived: unknown;
            service.ask('timed out query').subscribe({ error: (e) => (errorReceived = e) });

            const req = httpTesting.expectOne('api/iris/search-answer');
            req.flush(null, { status: 202, statusText: 'Accepted' });

            vi.advanceTimersByTime(30_001);

            expect(errorReceived).toBeDefined();
            vi.useRealTimers();
        });
    });
});
