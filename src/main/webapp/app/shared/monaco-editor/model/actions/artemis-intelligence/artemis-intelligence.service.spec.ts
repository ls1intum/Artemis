import { createComponent } from '@angular/core';
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
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementRewriteResponse } from 'app/openapi/model/problemStatementRewriteResponse';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { InlineConsistencyIssue, addCommentBox, addCommentBoxes, applySuggestedChangeToModel, isMatchingRepository, issuesForSelectedFile } from './consistency-check';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

jest.mock('@angular/core', () => {
    const actual = jest.requireActual('@angular/core');
    return {
        ...actual,
        createComponent: jest.fn(),
    };
});

describe('ArtemisIntelligenceService', () => {
    let httpMock: HttpTestingController;
    let service: ArtemisIntelligenceService;
    let websocketService: WebsocketService;
    let alertService: AlertService;

    const mockWebsocketService = {
        subscribe: jest.fn().mockReturnValue(
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

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.MethodReturnTypeMismatch,
            description: 'Description 1.',
            suggestedFix: 'Fix 1',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'template_repository/src/Class1.java',
                    startLine: 1,
                    endLine: 1,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'solution_repository/src/Class1.java',
                    startLine: 1,
                    endLine: 1,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'template_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'solution_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'tests_repository/src/Class2.java',
                    startLine: 1,
                    endLine: 2,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Description 2',
            suggestedFix: 'Fix 2',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.ProblemStatement,
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 3,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'tests_repository/src/Class3.java',
                    startLine: 1,
                    endLine: 3,
                },
            ],
        },
    ];

    const createModel = (content: string) => ({
        getLineContent: (lineNumber: number) => content.split('\n')[lineNumber - 1] ?? '',
        getValueInRange: (_range: any) => content,
        findMatches: (searchText: string) => {
            if (!content.includes(searchText)) {
                return [];
            }
            return [{ range: { startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: searchText.length + 1 } }];
        },
        pushEditOperations: jest.fn(),
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: HyperionProblemStatementApiService, useValue: mockHyperionApiService },
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        service = TestBed.inject(ArtemisIntelligenceService);
        websocketService = TestBed.inject(WebsocketService);
        alertService = TestBed.inject(AlertService);
        translateService = TestBed.inject(TranslateService);
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
            mockWebsocketService.subscribe.mockReturnValueOnce(throwError(() => new Error('WebSocket Error')));

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

        it('matches correct repositories', () => {
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.ProblemStatement, RepositoryType.TEMPLATE)).toBeFalsy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.ProblemStatement, RepositoryType.SOLUTION)).toBeFalsy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.SolutionRepository, RepositoryType.TEMPLATE)).toBeFalsy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.SolutionRepository, RepositoryType.TESTS)).toBeFalsy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.TestsRepository, RepositoryType.SOLUTION)).toBeFalsy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.TestsRepository, RepositoryType.TEMPLATE)).toBeFalsy();

            expect(isMatchingRepository(ArtifactLocation.TypeEnum.ProblemStatement, 'PROBLEM_STATEMENT')).toBeTruthy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.SolutionRepository, RepositoryType.SOLUTION)).toBeTruthy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.TemplateRepository, RepositoryType.TEMPLATE)).toBeTruthy();
            expect(isMatchingRepository(ArtifactLocation.TypeEnum.TestsRepository, RepositoryType.TESTS)).toBeTruthy();
        });

        it('correct issues for selected files: problem statement', () => {
            const res = issuesForSelectedFile('problem_statement.md', 'PROBLEM_STATEMENT', mockIssues);
            expect(res).toHaveLength(1);
            expect(res[0].type).toEqual(ArtifactLocation.TypeEnum.ProblemStatement);
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
            expect(res[0].type).toEqual(ArtifactLocation.TypeEnum.TemplateRepository);
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
            expect(res[0].type).toEqual(ArtifactLocation.TypeEnum.SolutionRepository);
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
            expect(res[0].type).toEqual(ArtifactLocation.TypeEnum.TestsRepository);
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

    describe('applySuggestedChangeToModel', () => {
        it('returns true when the range matches and applies the change', () => {
            const model = createModel('original');
            const issue: InlineConsistencyIssue = {
                filePath: 'path',
                type: ArtifactLocation.TypeEnum.TemplateRepository,
                startLine: 1,
                endLine: 1,
                description: 'desc',
                suggestedFix: 'fix',
                category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
                severity: ConsistencyIssue.SeverityEnum.Medium,
                originalText: 'original',
                modifiedText: 'updated',
            };

            const result = applySuggestedChangeToModel(model as any, issue);
            expect(result).toBeTrue();
            expect(model.pushEditOperations).toHaveBeenCalledOnce();
        });

        it('returns false when no match is found', () => {
            const model = createModel('some content');
            const issue: InlineConsistencyIssue = {
                filePath: 'path',
                type: ArtifactLocation.TypeEnum.TemplateRepository,
                startLine: 1,
                endLine: 1,
                description: 'desc',
                suggestedFix: 'fix',
                category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
                severity: ConsistencyIssue.SeverityEnum.Medium,
                originalText: 'missing',
                modifiedText: 'updated',
            };

            const result = applySuggestedChangeToModel(model as any, issue);
            expect(result).toBeFalse();
            expect(model.pushEditOperations).not.toHaveBeenCalled();
        });
    });

    describe('addCommentBox', () => {
        it('should create a comment widget and dispose it on cleanup', () => {
            const componentRef = {
                setInput: jest.fn(),
                changeDetectorRef: { detectChanges: jest.fn() },
                hostView: {},
                destroy: jest.fn(),
            };
            (createComponent as jest.Mock).mockReturnValue(componentRef as any);

            const appRef = {
                attachView: jest.fn(),
                detachView: jest.fn(),
            };
            const editor = {
                addLineWidget: jest.fn(),
            };
            const issue: InlineConsistencyIssue = {
                filePath: 'path',
                type: ArtifactLocation.TypeEnum.TemplateRepository,
                startLine: 1,
                endLine: 2,
                description: 'desc',
                suggestedFix: 'fix',
                category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
                severity: ConsistencyIssue.SeverityEnum.Medium,
            };

            addCommentBox(editor as any, issue, 1, jest.fn(), appRef as any, {} as any);

            expect(componentRef.setInput).toHaveBeenCalledWith('issue', issue);
            expect(componentRef.setInput).toHaveBeenCalledWith('onApply', expect.any(Function));
            expect(appRef.attachView).toHaveBeenCalledWith(componentRef.hostView);
            expect(editor.addLineWidget).toHaveBeenCalledWith(issue.endLine, 'comment-1', expect.any(HTMLElement), expect.any(Function));

            const onDispose = (editor.addLineWidget as jest.Mock).mock.calls[0][3];
            onDispose();

            expect(appRef.detachView).toHaveBeenCalledWith(componentRef.hostView);
            expect(componentRef.destroy).toHaveBeenCalled();
        });
    });

    describe('addCommentBoxes', () => {
        it('should derive original text when rendering issue comments', () => {
            const componentRef = {
                setInput: jest.fn(),
                changeDetectorRef: { detectChanges: jest.fn() },
                hostView: {},
                destroy: jest.fn(),
            };
            (createComponent as jest.Mock).mockReturnValue(componentRef as any);
            const editor = {
                getModel: () => ({
                    getLineContent: () => 'old text',
                    getValueInRange: () => 'old text',
                }),
                addLineWidget: jest.fn(),
            };
            const issues: ConsistencyIssue[] = [
                {
                    severity: ConsistencyIssue.SeverityEnum.Medium,
                    category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
                    description: 'desc',
                    suggestedFix: 'fix',
                    relatedLocations: [
                        {
                            type: ArtifactLocation.TypeEnum.TemplateRepository,
                            filePath: 'template_repository/src/Class1.java',
                            startLine: 1,
                            endLine: 1,
                            modifiedText: 'new text',
                        },
                    ],
                },
            ];
            const appRef = {
                attachView: jest.fn(),
                detachView: jest.fn(),
            };

            const onApply = jest.fn();
            const environmentInjector = {} as any;

            addCommentBoxes(editor as any, issues, 'src/Class1.java', RepositoryType.TEMPLATE, onApply, appRef as any, environmentInjector);

            expect(componentRef.setInput).toHaveBeenCalledWith(
                'issue',
                expect.objectContaining({
                    originalText: 'old text',
                    modifiedText: 'new text',
                }),
            );
        });
    });
});
