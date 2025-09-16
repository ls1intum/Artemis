import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { of, throwError } from 'rxjs';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HyperionReviewAndRefineApiService } from 'app/openapi/api/hyperionReviewAndRefineApi.service';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;
    let alertService: AlertService;

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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: HyperionReviewAndRefineApiService, useValue: mockHyperionApiService },
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        httpMock.verify();
        jest.clearAllMocks();
    });

    describe('rewrite', () => {
        it('should trigger rewriting pipeline and return rewritten text', () => {
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;
            const inputText = 'Original text';
            const mockResponse = {
                result: 'Rewritten Text',
            };
            service.rewrite(inputText, rewritingVariant, courseId).subscribe((result) => {
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
        it('should reflect loading state correctly for Hyperion consistency check', () => {
            const mockResponse: ConsistencyCheckResponse = { issues: [] };
            mockHyperionApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            expect(service.isLoading()).toBeFalsy();
            service.consistencyCheck(42).subscribe((res) => expect(res.issues).toEqual([]));
            expect(service.isLoading()).toBeFalsy(); // Should be false after synchronous completion
        });
    });
});
