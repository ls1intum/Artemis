import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { WebsocketService } from 'app/shared/service/websocket.service';

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;
    let websocketService: WebsocketService;

    const mockWebsocketService = {
        subscribe: jest.fn(),
        unsubscribe: jest.fn(),
        receive: jest.fn().mockReturnValue(
            new BehaviorSubject({
                result: 'Rewritten Text',
                inconsistencies: ['Some inconsistency'],
                suggestions: ['Suggestion 1'],
                improvement: 'Improved text',
            }),
        ),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
        websocketService = TestBed.inject(WebsocketService);
    });

    afterEach(() => {
        httpMock.verify();
        jest.clearAllMocks();
    });

    describe('rewrite', () => {
        it('should trigger rewriting pipeline and return rewritten text', () => {
            const courseId = 1;
            const inputText = 'Original text';
            const mockResponse = {
                rewrittenText: 'Rewritten Text',
            };

            service.rewrite(inputText, courseId).subscribe((result) => {
                expect(result).toEqual(mockResponse);
                expect(service.isLoading()).toBeFalse();
            });

            const req = httpMock.expectOne(`api/nebula/courses/${courseId}/rewrite-text`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({
                toBeRewritten: inputText,
            });

            req.flush(mockResponse);
        });

        it('should handle HTTP error correctly', () => {
            const courseId = 1;
            const inputText = 'Text to rewrite';
            const errorCallback = jest.fn();

            service.rewrite(inputText, courseId).subscribe({
                next: () => {
                    throw new Error('Expected error');
                },
                error: errorCallback,
            });

            const req = httpMock.expectOne(`api/nebula/courses/${courseId}/rewrite-text`);
            req.flush({ message: 'Error' }, { status: 500, statusText: 'Server Error' });

            expect(errorCallback).toHaveBeenCalled();
            expect(service.isLoading()).toBeFalse();
        });
    });

    describe('faqConsistencyCheck', () => {
        it('should trigger FAQ consistency check and return result', () => {
            const courseId = 2;
            const text = 'Some FAQ text';
            const mockResponse = {
                inconsistencies: ['i1'],
                suggestions: ['s1'],
                improvement: 'Better text',
            };

            service.faqConsistencyCheck(courseId, text).subscribe((result) => {
                expect(result).toEqual(mockResponse);
                expect(service.isLoading()).toBeFalse();
            });

            const req = httpMock.expectOne(`api/nebula/courses/${courseId}/consistency-check`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({ toBeChecked: text });

            req.flush(mockResponse);
        });

        it('should handle HTTP error in FAQ consistency check', () => {
            const courseId = 2;
            const text = 'FAQ to check';
            const errorCallback = jest.fn();

            service.faqConsistencyCheck(courseId, text).subscribe({
                error: errorCallback,
            });

            const req = httpMock.expectOne(`api/nebula/courses/${courseId}/consistency-check`);
            req.flush({ message: 'Error' }, { status: 400, statusText: 'Bad Request' });

            expect(errorCallback).toHaveBeenCalled();
            expect(service.isLoading()).toBeFalse();
        });
    });

    describe('consistencyCheck', () => {
        it('should trigger consistency check and return result', () => {
            const exerciseId = 42;
            mockWebsocketService.receive.mockReturnValueOnce(of({ result: 'Check Passed' }));
            service.consistencyCheck(exerciseId).subscribe((result) => {
                expect(result).toBe('Check Passed');
                expect(websocketService.subscribe).toHaveBeenCalledWith(`/user/topic/iris/consistency-check/exercises/${exerciseId}`);
                expect(websocketService.unsubscribe).toHaveBeenCalledWith(`/user/topic/iris/consistency-check/exercises/${exerciseId}`);
            });
            const req = httpMock.expectOne(`api/iris/consistency-check/exercises/${exerciseId}`);
            expect(req.request.method).toBe('POST');
            req.flush(null);
        });

        it('should handle WebSocket error during consistency check', () => {
            mockWebsocketService.receive.mockReturnValueOnce(throwError(() => new Error('WebSocket Error')));
            service.consistencyCheck(42).subscribe({
                next: () => {
                    throw new Error('Should not reach this point');
                },
                error: (err) => expect(err.message).toBe('WebSocket Error'),
            });
            const req = httpMock.expectOne(`api/iris/consistency-check/exercises/42`);
            req.flush(null);
        });

        it('should handle HTTP error and reset loading state', () => {
            const exerciseId = 42;
            const errorCallback = jest.fn();

            service.consistencyCheck(exerciseId).subscribe({
                error: errorCallback,
            });

            const req = httpMock.expectOne(`api/iris/consistency-check/exercises/${exerciseId}`);
            expect(req.request.method).toBe('POST');
            req.flush({ message: 'Error' }, { status: 500, statusText: 'Server Error' });

            expect(errorCallback).toHaveBeenCalled();
            expect(service.isLoading()).toBeFalse();
        });
    });

    describe('isLoading', () => {
        it('should reflect loading state correctly', () => {});
    });
});
