import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { BehaviorSubject, of, throwError } from 'rxjs';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { HyperionReviewAndRefineApiService } from 'app/openapi/api/hyperionReviewAndRefineApi.service';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';

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

    const mockHyperionApiService = {
        rewriteProblemStatement: jest.fn(),
        checkExerciseConsistency: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: HyperionReviewAndRefineApiService, useValue: mockHyperionApiService },
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
        it('should trigger rewriting pipeline for FAQ variant via Iris and return rewritten text', () => {
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

        it('should trigger rewriting pipeline for PROBLEM_STATEMENT variant via Hyperion and return rewritten text', () => {
            const toBeRewritten = 'Original problem statement';
            const rewritingVariant = RewritingVariant.PROBLEM_STATEMENT;
            const courseId = 42;

            const mockResponse: ProblemStatementRewriteResponse = {
                rewrittenText: 'Improved problem statement',
                improved: true,
            };

            mockHyperionApiService.rewriteProblemStatement.mockReturnValue(of(mockResponse));

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe((result) => {
                expect(result.result).toBe('Improved problem statement');
                expect(result.improvement).toBe('Text was improved');
                expect(result.inconsistencies).toBeUndefined();
                expect(result.suggestions).toBeUndefined();

                expect(alertService.success).toHaveBeenCalledWith('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                expect(mockHyperionApiService.rewriteProblemStatement).toHaveBeenCalledWith(courseId, {
                    problemStatementText: toBeRewritten,
                });
            });
        });

        it('should handle HTTP error correctly for FAQ variant', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe({
                error: (err) => expect(err.status).toBe(400),
            });

            const req = httpMock.expectOne(`api/iris/courses/${courseId}/rewrite-text`);
            req.flush({ message: 'Error' }, { status: 400, statusText: 'Bad Request' });
        });

        it('should handle WebSocket error correctly', () => {
            mockWebsocketService.receive.mockReturnValueOnce(throwError(() => new Error('WebSocket Error')));

            service.rewrite('OriginalText', RewritingVariant.FAQ, 1).subscribe({
                next: () => {
                    throw new Error('Should not reach this point');
                },
                error: (err) => expect(err.message).toBe('WebSocket Error'),
            });

            const req = httpMock.expectOne(`api/iris/courses/1/rewrite-text`);
            req.flush(null);

            expect(websocketService.subscribe).toHaveBeenCalled();
        });
    });

    describe('consistencyCheck', () => {
        it('should trigger consistency check using Hyperion API and return result', () => {
            const exerciseId = 42;
            const mockResponse: ConsistencyCheckResponse = { issues: [] };

            mockHyperionApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            service.consistencyCheck(exerciseId).subscribe((result) => {
                expect(result.issues).toEqual([]);
                expect(mockHyperionApiService.checkExerciseConsistency).toHaveBeenCalledWith(exerciseId);
            });
        });

        it('should handle errors during consistency check', () => {
            const exerciseId = 42;
            const error = new Error('API Error');

            mockHyperionApiService.checkExerciseConsistency.mockReturnValue(throwError(() => error));

            service.consistencyCheck(exerciseId).subscribe({
                next: () => {
                    throw new Error('Should not reach this point');
                },
                error: (err) => expect(err).toBe(error),
            });
        });

        it('should reset loading state after consistency check completes', () => {
            const exerciseId = 42;
            const mockIssue = {
                severity: 'HIGH',
                category: 'METHOD_PARAMETER_MISMATCH',
                description: 'Test issue',
                suggestedFix: 'Fix this',
                relatedLocations: [],
            } as const;
            // Cast to any because openapi types are structural; keeping literals preserves intent while avoiding enum import complexity in spec.
            const mockResponse: ConsistencyCheckResponse = { issues: [mockIssue as any] };

            mockHyperionApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            service.consistencyCheck(exerciseId).subscribe(() => {
                expect(service.isLoading()).toBeFalsy();
            });
        });
    });

    describe('isLoading', () => {
        it('should reflect loading state correctly for FAQ rewrite', () => {
            expect(service.isLoading()).toBeFalsy();
            const subscription = service.rewrite('test', RewritingVariant.FAQ, 1).subscribe();
            expect(service.isLoading()).toBeTruthy();
            const req = httpMock.expectOne(`api/iris/courses/1/rewrite-text`);
            req.flush(null);
            subscription.unsubscribe();
            expect(service.isLoading()).toBeFalsy();
        });

        it('should reflect loading state correctly for Hyperion consistency check', () => {
            const mockResponse: ConsistencyCheckResponse = { issues: [] };
            mockHyperionApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            expect(service.isLoading()).toBeFalsy();
            service.consistencyCheck(42).subscribe((res) => expect(res.issues).toEqual([]));
            expect(service.isLoading()).toBeFalsy(); // Should be false after synchronous completion
        });
    });
});
