import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject } from 'rxjs';
import { IRIS_SEARCH_ANSWER_WS_TIMEOUT_MS, IrisSearchAnswerService } from './iris-search-answer.service';
import { IrisSearchStatusUpdate } from 'app/core/navbar/global-search/models/iris-search-status-update.model';
import { WebsocketService } from 'app/shared/service/websocket.service';

const MOCK_RUN_ID = '550e8400-e29b-41d4-a716-446655440000';

describe('IrisSearchAnswerService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisSearchAnswerService;
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
            providers: [IrisSearchAnswerService, provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: mockWebsocketService }],
        });
        service = TestBed.inject(IrisSearchAnswerService);
        httpTesting = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpTesting.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should POST to api/iris/search-answer with query, default limit of 5, and client-generated runId', () => {
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

        const req = httpTesting.expectOne('api/iris/search-answer');
        req.flush(null, { status: 202, statusText: 'Accepted' });

        wsSubject.next(mockResult);

        expect(actualResult).toEqual(mockResult);
    });

    it('should error if the WebSocket does not respond within the timeout', () => {
        vi.useFakeTimers();
        let errorReceived: unknown;
        service.ask('timed out query').subscribe({ error: (e) => (errorReceived = e) });

        const req = httpTesting.expectOne('api/iris/search-answer');
        req.flush(null, { status: 202, statusText: 'Accepted' });

        vi.advanceTimersByTime(IRIS_SEARCH_ANSWER_WS_TIMEOUT_MS + 1);

        expect(errorReceived).toBeDefined();
        vi.useRealTimers();
    });
});
