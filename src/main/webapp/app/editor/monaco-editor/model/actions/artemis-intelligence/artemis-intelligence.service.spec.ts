import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { of, throwError } from 'rxjs';
import RewritingVariant from 'app/editor/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HyperionProblemStatementApi } from 'app/openapi/api/hyperion-problem-statement-api';
import { ProblemStatementRewriteResponse } from 'app/openapi/models/problem-statement-rewrite-response';
import { ConsistencyCheckResponse } from 'app/openapi/models/consistency-check-response';
import {
    InlineConsistencyIssue,
    addCommentBoxes,
    formatArtifactType,
    formatConsistencyCheckResults,
    humanizeCategory,
    isMatchingRepository,
    issuesForSelectedFile,
    severityToString,
} from './consistency-check';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ConsistencyIssue } from 'app/openapi/models/consistency-issue';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { RewriteFaqResponse } from 'app/openapi/models/rewrite-faq-response';
import { HyperionFaqApi } from 'app/openapi/api/hyperion-faq-api';

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;
    let alertService: AlertService;
    let translateService: TranslateService;

    const monacoEditorComponent = {
        addLineWidget: vi.fn(),
    } as unknown as MonacoEditorComponent;

    const mockAlertService = {
        success: vi.fn(),
    };

    const mockHyperionFaqApiService = {
        rewriteFaq: vi.fn(),
    };

    const mockHyperionProblemStatementApiService = {
        rewriteProblemStatement: vi.fn(),
        checkExerciseConsistency: vi.fn(),
    };

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: 'HIGH',
            category: 'METHOD_RETURN_TYPE_MISMATCH',
            description: 'Description 1.',
            suggestedFix: 'Fix 1',
            relatedLocations: [
                {
                    type: 'TEMPLATE_REPOSITORY',
                    filePath: 'template_repository/src/Class1.java',
                    startLine: 1,
                    endLine: 1,
                },
                {
                    type: 'SOLUTION_REPOSITORY',
                    filePath: 'solution_repository/src/Class1.java',
                    startLine: 1,
                    endLine: 1,
                },
            ],
        },
        {
            severity: 'MEDIUM',
            category: 'ATTRIBUTE_TYPE_MISMATCH',
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: 'TEMPLATE_REPOSITORY',
                    filePath: 'template_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: 'SOLUTION_REPOSITORY',
                    filePath: 'solution_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: 'TESTS_REPOSITORY',
                    filePath: 'tests_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
            ],
        },
        {
            severity: 'LOW',
            category: 'VISIBILITY_MISMATCH',
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: 'PROBLEM_STATEMENT',
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 3,
                },
                {
                    type: 'TESTS_REPOSITORY',
                    filePath: 'tests_repository/src/Class3.java',
                    startLine: 1,
                    endLine: 3,
                },
            ],
        },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: HyperionFaqApi, useValue: mockHyperionFaqApiService },
                { provide: HyperionProblemStatementApi, useValue: mockHyperionProblemStatementApiService },
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
        alertService = TestBed.inject(AlertService);
        translateService = TestBed.inject(TranslateService);
    });

    afterEach(() => {
        httpMock.verify();
        vi.clearAllMocks();
    });

    describe('rewrite', () => {
        it('should trigger rewriting pipeline for FAQ variant via Hyperion and return rewritten text', () => {
            const toBeRewritten = 'OriginalText';
            const rewritingVariant = RewritingVariant.FAQ;
            const courseId = 1;

            const mockResponse: RewriteFaqResponse = {
                rewrittenText: 'Rewritten Text',
                inconsistencies: ['Some inconsistency'],
                suggestions: ['Suggestion 1'],
                improvement: 'Improved text',
            };

            mockHyperionFaqApiService.rewriteFaq.mockReturnValue(of(mockResponse));

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe((result) => {
                expect(result.result).toBe('Rewritten Text');
                expect(result.inconsistencies).toEqual(['Some inconsistency']);
                expect(result.suggestions).toEqual(['Suggestion 1']);
                expect(result.improvement).toBe('Improved text');

                expect(alertService.success).toHaveBeenCalledWith('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                expect(mockHyperionFaqApiService.rewriteFaq).toHaveBeenCalledWith(courseId, {
                    faqText: toBeRewritten,
                });
            });
        });

        it('should trigger rewriting pipeline for PROBLEM_STATEMENT variant via Hyperion and return rewritten text', () => {
            const toBeRewritten = 'Original problem statement';
            const rewritingVariant = RewritingVariant.PROBLEM_STATEMENT;
            const courseId = 42;

            const mockResponse: ProblemStatementRewriteResponse = {
                rewrittenText: 'Improved problem statement',
                improved: true,
            };

            mockHyperionProblemStatementApiService.rewriteProblemStatement.mockReturnValue(of(mockResponse));

            service.rewrite(toBeRewritten, rewritingVariant, courseId).subscribe((result) => {
                expect(result.result).toBe('Improved problem statement');
                expect(result.improvement).toBe('Text was improved');
                expect(result.inconsistencies).toBeUndefined();
                expect(result.suggestions).toBeUndefined();

                expect(alertService.success).toHaveBeenCalledWith('artemisApp.markdownEditor.artemisIntelligence.alerts.rewrite.success');
                expect(mockHyperionProblemStatementApiService.rewriteProblemStatement).toHaveBeenCalledWith(courseId, {
                    problemStatementText: toBeRewritten,
                });
            });
        });
    });

    describe('consistencyCheck', () => {
        it('should trigger consistency check using Hyperion API and return result', () => {
            const exerciseId = 42;
            const mockResponse: ConsistencyCheckResponse = { timestamp: new Date().toISOString(), issues: [] };

            mockHyperionProblemStatementApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            service.consistencyCheck(exerciseId).subscribe((result) => {
                expect(result.issues).toEqual([]);
                expect(mockHyperionProblemStatementApiService.checkExerciseConsistency).toHaveBeenCalledWith(exerciseId);
            });
        });

        it('should handle errors during consistency check', () => {
            const exerciseId = 42;
            const error = new Error('API Error');

            mockHyperionProblemStatementApiService.checkExerciseConsistency.mockReturnValue(throwError(() => error));

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
            const mockResponse: ConsistencyCheckResponse = { timestamp: new Date().toISOString(), issues: [mockIssue as any] };

            mockHyperionProblemStatementApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            // The synchronous observable resets the loading flag in finalize() after the value is delivered, so the
            // state is asserted once the (synchronous) subscription has completed rather than inside the next handler.
            service.consistencyCheck(exerciseId).subscribe();
            expect(service.isLoading()).toBeFalsy();
        });

        it('matches correct repositories', () => {
            expect(isMatchingRepository('PROBLEM_STATEMENT', RepositoryType.TEMPLATE)).toBeFalsy();
            expect(isMatchingRepository('PROBLEM_STATEMENT', RepositoryType.SOLUTION)).toBeFalsy();
            expect(isMatchingRepository('SOLUTION_REPOSITORY', RepositoryType.TEMPLATE)).toBeFalsy();
            expect(isMatchingRepository('SOLUTION_REPOSITORY', RepositoryType.TESTS)).toBeFalsy();
            expect(isMatchingRepository('TESTS_REPOSITORY', RepositoryType.SOLUTION)).toBeFalsy();
            expect(isMatchingRepository('TESTS_REPOSITORY', RepositoryType.TEMPLATE)).toBeFalsy();

            expect(isMatchingRepository('PROBLEM_STATEMENT', 'PROBLEM_STATEMENT')).toBeTruthy();
            expect(isMatchingRepository('SOLUTION_REPOSITORY', RepositoryType.SOLUTION)).toBeTruthy();
            expect(isMatchingRepository('TEMPLATE_REPOSITORY', RepositoryType.TEMPLATE)).toBeTruthy();
            expect(isMatchingRepository('TESTS_REPOSITORY', RepositoryType.TESTS)).toBeTruthy();
        });

        it('severity to string correct', () => {
            expect(severityToString('MEDIUM')).toBe('MEDIUM');
            expect(severityToString('LOW')).toBe('LOW');
            expect(severityToString('HIGH')).toBe('HIGH');
            expect(severityToString(undefined as any)).toBe('UNKNOWN');
        });

        it('humanized category correctly', () => {
            expect(humanizeCategory('IDENTIFIER_NAMING_INCONSISTENCY')).toBe('Identifier Naming Inconsistency');
            expect(humanizeCategory('VISIBILITY_MISMATCH')).toBe('Visibility Mismatch');
            expect(humanizeCategory('METHOD_PARAMETER_MISMATCH')).toBe('Method Parameter Mismatch');
            expect(humanizeCategory('GENERAL')).toBe('General');
        });

        it('format artifact type correctly', () => {
            expect(formatArtifactType('TESTS_REPOSITORY')).toBe('Tests');
            expect(formatArtifactType('PROBLEM_STATEMENT')).toBe('Problem Statement');
            expect(formatArtifactType('SOLUTION_REPOSITORY')).toBe('Solution');
            expect(formatArtifactType('TEMPLATE_REPOSITORY')).toBe('Template');
            expect(formatArtifactType(undefined as any)).toBe('Other');
        });

        it('correct issues for selected files: problem statement', () => {
            const res = issuesForSelectedFile('problem_statement.md', 'PROBLEM_STATEMENT', mockIssues);
            expect(res).toHaveLength(1);
            expect(res[0].type).toEqual('PROBLEM_STATEMENT');
            expect(res[0].startLine).toBe(1);
            expect(res[0].endLine).toBe(3);
            expect(res[0].category).toEqual(mockIssues[2].category);
            expect(res[0].severity).toEqual(mockIssues[2].severity);
            expect(res[0].description).toEqual(mockIssues[2].description);
            expect(res[0].suggestedFix).toEqual(mockIssues[2].suggestedFix);
        });

        it('correct issues for selected files: template', () => {
            const res = issuesForSelectedFile('src/Class2.java', RepositoryType.TEMPLATE, mockIssues);
            expect(res).toHaveLength(1);
            expect(res[0].type).toEqual('TEMPLATE_REPOSITORY');
            expect(res[0].startLine).toBe(1);
            expect(res[0].endLine).toBe(2);
            expect(res[0].category).toEqual(mockIssues[1].category);
            expect(res[0].severity).toEqual(mockIssues[1].severity);
            expect(res[0].description).toEqual(mockIssues[1].description);
            expect(res[0].suggestedFix).toEqual(mockIssues[1].suggestedFix);
        });

        it('correct issues for selected files: solution', () => {
            const res = issuesForSelectedFile('src/Class1.java', RepositoryType.SOLUTION, mockIssues);
            expect(res).toHaveLength(1);
            expect(res[0].type).toEqual('SOLUTION_REPOSITORY');
            expect(res[0].startLine).toBe(1);
            expect(res[0].endLine).toBe(1);
            expect(res[0].category).toEqual(mockIssues[0].category);
            expect(res[0].severity).toEqual(mockIssues[0].severity);
            expect(res[0].description).toEqual(mockIssues[0].description);
            expect(res[0].suggestedFix).toEqual(mockIssues[0].suggestedFix);
        });

        it('correct issues for selected files: tests', () => {
            const res = issuesForSelectedFile('src/Class3.java', RepositoryType.TESTS, mockIssues);
            expect(res).toHaveLength(1);
            expect(res[0].type).toEqual('TESTS_REPOSITORY');
            expect(res[0].startLine).toBe(1);
            expect(res[0].endLine).toBe(3);
            expect(res[0].description).toEqual(mockIssues[2].description);
            expect(res[0].suggestedFix).toEqual(mockIssues[2].suggestedFix);
            expect(res[0].category).toEqual(mockIssues[2].category);
            expect(res[0].severity).toEqual(mockIssues[2].severity);
        });

        it('correct issues for selected files: undefined', () => {
            const res = issuesForSelectedFile(undefined, RepositoryType.TEMPLATE, mockIssues);
            expect(res).toHaveLength(0);

            const res2 = issuesForSelectedFile('template_repository/src/Class2.java', undefined, mockIssues);
            expect(res2).toHaveLength(0);
        });

        it('format contains necessary information', () => {
            const mockIssue: InlineConsistencyIssue = {
                filePath: 'path',
                type: 'TEMPLATE_REPOSITORY',
                startLine: 1,
                endLine: 3,
                description: 'Example description',
                suggestedFix: 'Example fix',
                category: 'ATTRIBUTE_TYPE_MISMATCH',
                severity: 'MEDIUM',
            };

            const res = formatConsistencyCheckResults(mockIssue);

            expect(res).toContain(mockIssue.description);
            expect(res).toContain(mockIssue.suggestedFix);
            expect(res).toContain(humanizeCategory(mockIssue.category));
            expect(res).toContain(severityToString(mockIssue.severity));
            expect(res).toContain(String(mockIssue.startLine));
            expect(res).toContain(String(mockIssue.endLine));
        });

        it('addCommentBoxes calls correct functions', () => {
            addCommentBoxes(monacoEditorComponent, mockIssues, 'problem_statement.md', 'PROBLEM_STATEMENT', translateService);
            expect(monacoEditorComponent.addLineWidget).toHaveBeenCalledOnce();
        });
    });

    describe('isLoading', () => {
        it('should reflect loading state correctly for FAQ rewrite', () => {
            const mockResponse: RewriteFaqResponse = {
                rewrittenText: 'Rewritten Text',
                inconsistencies: [],
                suggestions: [],
                improvement: '',
            };
            mockHyperionFaqApiService.rewriteFaq.mockReturnValue(of(mockResponse));

            expect(service.isLoading()).toBeFalsy();
            service.rewrite('test', RewritingVariant.FAQ, 1).subscribe((res) => expect(res.result).toBe('Rewritten Text'));
            expect(service.isLoading()).toBeFalsy();
        });

        it('should reflect loading state correctly for Hyperion consistency check', () => {
            const mockResponse: ConsistencyCheckResponse = { timestamp: new Date().toISOString(), issues: [] };
            mockHyperionProblemStatementApiService.checkExerciseConsistency.mockReturnValue(of(mockResponse));

            expect(service.isLoading()).toBeFalsy();
            service.consistencyCheck(42).subscribe((res) => expect(res.issues).toEqual([]));
            expect(service.isLoading()).toBeFalsy(); // Should be false after synchronous completion
        });
    });
});
