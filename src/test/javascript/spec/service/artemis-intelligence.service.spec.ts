import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { WebsocketService } from 'app/core/websocket/websocket.service';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;
    let websocketService: WebsocketService;
    let alertService: AlertService;

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

    const mockAlertService = {
        success: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: mockAlertService },
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
        websocketService = TestBed.inject(WebsocketService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        httpMock.verify();
        jest.clearAllMocks();
    });

    describe('rewrite', () => {
        it('should trigger rewriting pipeline and return rewritten text', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe((result) => {
                expect(result.result).toBe('Rewritten Text');
                expect(result.inconsistencies).toEqual(['Some inconsistency']);
                expect(result.suggestions).toEqual(['Suggestion 1']);
                expect(result.improvement).toBe('Improved text');

                expect(alertService.success).toHaveBeenCalledWith('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                expect(websocketService.unsubscribe).toHaveBeenCalledWith(`/user/topic/iris/rewriting/${courseId}`);
            });

            const req = httpMock.expectOne(`api/iris/courses/${courseId}/rewrite-text`);
            expect(req.request.method).toBe('POST');
            req.flush(null);
        });

        it('should handle HTTP error correctly', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe({
                next: () => fail('Expected an error'),
                error: (err) => expect(err.status).toBe(400),
            });

            const req = httpMock.expectOne(`api/iris/courses/${courseId}/rewrite-text`);
            req.flush({ message: 'Error' }, { status: 400, statusText: 'Bad Request' });
        });

        it('should handle WebSocket error correctly', () => {
            mockWebsocketService.receive.mockReturnValueOnce(throwError(() => new Error('WebSocket Error')));

            service.rewrite('OriginalText', RewritingVariant.FAQ, 1).subscribe({
                next: () => fail('Expected an error'),
                error: (err) => expect(err.message).toBe('WebSocket Error'),
            });

            const req = httpMock.expectOne(`api/iris/courses/1/rewrite-text`);
            req.flush(null);

            expect(websocketService.subscribe).toHaveBeenCalled();
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
                next: () => fail('Expected an error'),
                error: (err) => expect(err.message).toBe('WebSocket Error'),
            });

            const req = httpMock.expectOne(`api/iris/consistency-check/exercises/42`);
            req.flush(null);
        });
    });

    describe('isLoading', () => {
        it('should reflect loading state correctly', () => {
            expect(service.isLoading()).toBe(false);

            const subscription = service.rewrite('test', RewritingVariant.FAQ, 1).subscribe();
            expect(service.isLoading()).toBe(true);

            const req = httpMock.expectOne(`api/iris/courses/1/rewrite-text`);
            req.flush(null);
            subscription.unsubscribe();

            expect(service.isLoading()).toBe(false);
        });
    });
});
