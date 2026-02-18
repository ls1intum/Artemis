import 'zone.js';
import 'zone.js/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { Subject, of, throwError } from 'rxjs';

import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { MockCourseExerciseService } from 'test/helpers/mocks/service/mock-course-exercise.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { Location } from '@angular/common';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { WritableSignal, signal } from '@angular/core';

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let codeGenerationApi: jest.Mocked<Pick<HyperionCodeGenerationApiService, 'generateCode'>>;
    let ws: jest.Mocked<Pick<HyperionWebsocketService, 'subscribeToJob' | 'unsubscribeFromJob'>>;
    let alertService: AlertService;
    let repoService: CodeEditorRepositoryService;
    let profileService: ProfileService;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let reviewCommentService: jest.Mocked<Pick<ExerciseReviewCommentService, 'setExercise' | 'reloadThreads'>> & { threads: WritableSignal<any[]> };

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.ConstructorParameterMismatch,
            description: 'Problem statement inconsistency',
            suggestedFix: 'Review the problem statement file.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.ProblemStatement,
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 42,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.MethodParameterMismatch,
            description: 'Template repository issue',
            suggestedFix: 'Fix template repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TemplateRepository,
                    filePath: 'src/template/Example.java',
                    startLine: 5,
                    endLine: 50,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Medium,
            category: ConsistencyIssue.CategoryEnum.AttributeTypeMismatch,
            description: 'Solution repository issue',
            suggestedFix: 'Fix solution repository references.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/solution/Solution.java',
                    startLine: 3,
                    endLine: 60,
                },
            ],
        },
        {
            severity: ConsistencyIssue.SeverityEnum.Low,
            category: ConsistencyIssue.CategoryEnum.IdentifierNamingInconsistency,
            description: 'Tests repository issue',
            suggestedFix: 'Adjust tests in test repository.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/tests/ExampleTest.java',
                    startLine: 10,
                    endLine: 70,
                },
            ],
        },
        {
            // A multi-location issue for testing next/previous navigation
            severity: ConsistencyIssue.SeverityEnum.High,
            category: ConsistencyIssue.CategoryEnum.VisibilityMismatch,
            description: 'Multi-location navigation test issue',
            suggestedFix: 'Resolve inconsistencies across artifacts.',
            relatedLocations: [
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/A.java',
                    startLine: 10,
                    endLine: 20,
                },
                {
                    type: ArtifactLocation.TypeEnum.TestsRepository,
                    filePath: 'src/template/B.java',
                    startLine: 30,
                    endLine: 40,
                },
                {
                    type: ArtifactLocation.TypeEnum.SolutionRepository,
                    filePath: 'src/template/C.java',
                    startLine: 50,
                    endLine: 60,
                },
            ],
        },
    ];

    const createConsistencyThreads = (issues: ConsistencyIssue[]) =>
        issues.map((issue, index) => {
            const firstLocation = issue.relatedLocations[0];
            const targetType = (() => {
                switch (firstLocation?.type) {
                    case ArtifactLocation.TypeEnum.TemplateRepository:
                        return CommentThreadLocationType.TEMPLATE_REPO;
                    case ArtifactLocation.TypeEnum.SolutionRepository:
                        return CommentThreadLocationType.SOLUTION_REPO;
                    case ArtifactLocation.TypeEnum.TestsRepository:
                        return CommentThreadLocationType.TEST_REPO;
                    case ArtifactLocation.TypeEnum.ProblemStatement:
                    default:
                        return CommentThreadLocationType.PROBLEM_STATEMENT;
                }
            })();

            const lineNumber = firstLocation?.endLine ?? firstLocation?.startLine ?? 1;
            const filePath = targetType === CommentThreadLocationType.PROBLEM_STATEMENT ? undefined : firstLocation?.filePath;
            const timestamp = new Date(2024, 0, index + 1).toISOString();

            return {
                id: index + 1,
                exerciseId: 42,
                targetType,
                filePath,
                initialFilePath: filePath,
                lineNumber,
                initialLineNumber: lineNumber,
                outdated: false,
                resolved: false,
                comments: [
                    {
                        id: index + 1_000,
                        threadId: index + 1,
                        type: CommentType.CONSISTENCY_CHECK,
                        authorName: 'Hyperion',
                        createdDate: timestamp,
                        lastModifiedDate: timestamp,
                        content: {
                            contentType: CommentContentType.CONSISTENCY_CHECK,
                            severity: issue.severity,
                            category: issue.category,
                            text: issue.description,
                        },
                    },
                ],
            };
        });

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // already initialized in some runners
        }
    });

    beforeEach(async () => {
        reviewCommentService = {
            setExercise: jest.fn(),
            reloadThreads: jest.fn(),
            threads: signal([]),
        } as any;

        await TestBed.configureTestingModule({
            imports: [CodeEditorInstructorAndEditorContainerComponent],
            providers: [
                { provide: AlertService, useClass: MockAlertService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: Router, useClass: MockRouter },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: CourseExerciseService, useClass: MockCourseExerciseService },
                { provide: DomainService, useValue: { setDomain: jest.fn() } },
                { provide: Location, useValue: { replaceState: jest.fn() } },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ActivatedRoute, useValue: { params: of({}) } },
                { provide: HyperionCodeGenerationApiService, useValue: { generateCode: jest.fn() } },
                { provide: NgbModal, useValue: { open: jest.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
                { provide: HyperionWebsocketService, useValue: { subscribeToJob: jest.fn(), unsubscribeFromJob: jest.fn() } },
                { provide: CodeEditorRepositoryService, useValue: { pull: jest.fn(() => of(void 0)) } },
                { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: jest.fn(() => of({} as any)) } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: jest.fn() } },
                { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: jest.fn(), isLoading: () => false } },
                { provide: ExerciseReviewCommentService, useValue: reviewCommentService },
            ],
        })
            // Avoid rendering heavy template dependencies for these tests
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
                set: {
                    template: '',
                    imports: [] as any,
                    providers: [{ provide: ExerciseReviewCommentService, useValue: reviewCommentService }],
                },
            })
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        codeGenerationApi = TestBed.inject(HyperionCodeGenerationApiService) as any;
        ws = TestBed.inject(HyperionWebsocketService) as any;
        profileService = TestBed.inject(ProfileService);
        repoService = TestBed.inject(CodeEditorRepositoryService) as any;
        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);

        // Enable Hyperion by default so property initialization is deterministic
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = { id: 42 } as any;

        // Mock codeEditorContainer and editableInstructions
        (comp as any).codeEditorContainer = {
            actions: { executeRefresh: jest.fn() },
            selectedFile: undefined as string | undefined,
            selectedRepository: jest.fn().mockReturnValue('SOLUTION'),
            problemStatementIdentifier: 'problem_statement.md',
            jumpToLine: jest.fn(),
            monacoEditor: {
                clearReviewCommentDrafts: jest.fn(),
            },
        };

        (comp as any).editableInstructions = {
            jumpToLine: jest.fn(),
            clearReviewCommentDrafts: jest.fn(),
        };

        // Mock jump helper methods
        comp.selectTemplateParticipation = jest.fn().mockResolvedValue(undefined);
        comp.selectSolutionParticipation = jest.fn().mockResolvedValue(undefined);
        comp.selectTestRepository = jest.fn().mockResolvedValue(undefined);
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

    describe('Code Generation', () => {
        it('should not generate when no exercise id', async () => {
            comp.exercise = undefined as any;
            comp.selectedRepository = RepositoryType.TEMPLATE;

            comp.generateCode();
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        });

        it('should not generate when a generation is already running', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            comp.isGeneratingCode.set(true);

            comp.generateCode();
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        });

        it('should warn on unsupported repository types', () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.ASSIGNMENT; // unsupported

            comp.generateCode();

            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' }),
            );
        });

        it('should call API, subscribe, and show success alert on DONE success', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;

            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-1' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve(); // resolve modal

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });

            // Emit DONE success event
            job$.next({ type: 'DONE', success: true });

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.SUCCESS,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                    translationParams: { repositoryType: RepositoryType.TEMPLATE },
                }),
            );
        });

        it('should call API, subscribe, and show partial success alert on DONE failure', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;

            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-2' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.SOLUTION });

            job$.next({ type: 'DONE', success: false });

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                    translationParams: { repositoryType: RepositoryType.SOLUTION },
                }),
            );
        });

        it('should show error alert on API error', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;

            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(throwError(() => new Error('fail')) as any);

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TESTS });
            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                }),
            );
        });

        it('should compute hyperionEnabled from profile service', async () => {
            // Already spied in beforeEach, but we can re-verify or override if needed
            // The component reads this property on initialization (property initializer).
            // Since beforeEach recreates the component, it should be true.
            expect(comp.hyperionEnabled).toBeTrue();
        });

        it('should compute hyperionEnabled as false when feature disabled', () => {
            jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Recreate the component so the property initializer runs with the new spy value
            fixture.destroy();
            fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
            comp = fixture.componentInstance;

            expect(comp.hyperionEnabled).toBeFalse();
        });

        it('should trigger repository pull on FILE_UPDATED and NEW_FILE events', async () => {
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-3' }));

            const job$ = new Subject<any>();
            (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());
            const pullSpy = jest.spyOn(repoService, 'pull');

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'FILE_UPDATED' });
            job$.next({ type: 'NEW_FILE' });

            expect(pullSpy).toHaveBeenCalledTimes(2);
        });

        it('should show modal when code generation is already running', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            const modalService = TestBed.inject(NgbModal);
            const openSpy = jest.spyOn(modalService, 'open');
            comp.selectedRepository = RepositoryType.TEMPLATE;

            const conflict = new HttpErrorResponse({
                status: 409,
                error: { 'X-artemisApp-error': 'error.codeGenerationRunning' },
            });
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(throwError(() => conflict) as any);

            comp.generateCode();
            await Promise.resolve();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });
            expect(comp.isGeneratingCode()).toBeFalse();
            // One modal from generateCode() confirmation and one from the "already running" error handler.
            expect(openSpy).toHaveBeenCalledTimes(2);
            expect(addAlertSpy).not.toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                }),
            );
        });

        it('should call executeRefresh and cleanup on DONE', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-4' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

            const executeRefresh = (comp as any).codeEditorContainer.actions.executeRefresh;

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'DONE', success: true });
            await Promise.resolve();

            expect(executeRefresh).toHaveBeenCalled();
            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-4');
        });

        it('should show danger alert and cleanup on ERROR event', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-5' }));
            const job$ = new Subject<any>();
            (ws.subscribeToJob as jest.Mock).mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'ERROR' });
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-5');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show danger alert and cleanup when job stream errors', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-6' }));
            (ws.subscribeToJob as jest.Mock).mockReturnValue(throwError(() => new Error('ws')));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-6');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show danger alert when response has no job id', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({}));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBeFalse();
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show timeout warning and cleanup when generation exceeds time limit', async () => {
            const addAlertSpy = jest.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({ jobId: 'job-7' }));

            // Intercept setTimeout to capture the scheduled callback and invoke it immediately
            const originalSetTimeout = window.setTimeout;
            let timeoutCallback: (() => void) | undefined;
            // @ts-ignore
            window.setTimeout = ((fn: () => void, _delay?: number) => {
                timeoutCallback = fn;
                return 1 as any;
            }) as any;

            try {
                (ws.subscribeToJob as jest.Mock).mockReturnValue(new Subject<any>().asObservable());
                comp.generateCode();
                await Promise.resolve();
                expect(comp.isGeneratingCode()).toBeTrue();

                // Simulate timeout
                if (timeoutCallback) {
                    timeoutCallback();
                }

                expect(comp.isGeneratingCode()).toBeFalse();
                expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-7');
                expect(addAlertSpy).toHaveBeenCalledWith(
                    expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }),
                );
            } finally {
                window.setTimeout = originalSetTimeout;
            }
        });

        it('should clear subscription when restore check-only has no active job', () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            const clearSpy = jest.spyOn(comp as any, 'clearJobSubscription');
            (codeGenerationApi.generateCode as jest.Mock).mockReturnValue(of({}));

            (comp as any).restoreCodeGenerationState();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.SOLUTION, checkOnly: true });
            expect(clearSpy).toHaveBeenCalledWith(true);
        });
    });

    describe('Review Comments', () => {
        it('loadExercise sets review context and reloads threads when returned exercise has an id', () => {
            const superLoadSpy = jest.spyOn(CodeEditorInstructorBaseContainerComponent.prototype, 'loadExercise').mockReturnValue(of({ id: 55 } as any));

            comp.loadExercise(55).subscribe();

            expect(superLoadSpy).toHaveBeenCalledWith(55);
            expect(reviewCommentService.setExercise).toHaveBeenCalledWith(55);
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();

            superLoadSpy.mockRestore();
        });

        it('loadExercise does not set review context when returned exercise has no id', () => {
            const superLoadSpy = jest.spyOn(CodeEditorInstructorBaseContainerComponent.prototype, 'loadExercise').mockReturnValue(of({} as any));

            comp.loadExercise(55).subscribe();

            expect(superLoadSpy).toHaveBeenCalledWith(55);
            expect(reviewCommentService.setExercise).not.toHaveBeenCalled();
            expect(reviewCommentService.reloadThreads).not.toHaveBeenCalled();

            superLoadSpy.mockRestore();
        });

        it('onCommit clears draft widgets and reloads threads', () => {
            const clearEditorDraftsSpy = jest.spyOn((comp as any).codeEditorContainer.monacoEditor, 'clearReviewCommentDrafts');

            comp.onCommit();

            expect(clearEditorDraftsSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        });

        it('onProblemStatementSaved clears markdown drafts and reloads threads', () => {
            const clearInstructionDraftsSpy = jest.spyOn((comp as any).editableInstructions, 'clearReviewCommentDrafts');

            comp.onProblemStatementSaved();

            expect(clearInstructionDraftsSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        });
    });

    describe('Consistency Checks', () => {
        const error1 = new ConsistencyCheckError();
        error1.programmingExercise = { id: 42 } as any;
        error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

        it('runs full consistency check and shows success when no issues', () => {
            const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const successSpy = jest.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);
            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);
            expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        });

        it('error when first consistency check fails', () => {
            const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const failSpy = jest.spyOn(alertService, 'error');

            comp.checkConsistencies(comp.exercise!);
            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).not.toHaveBeenCalled();
        });

        it('error when exercise id undefined', () => {
            const check1Spy = jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            const failSpy = jest.spyOn(alertService, 'error');

            comp.checkConsistencies({ id: undefined } as any);

            expect(check1Spy).not.toHaveBeenCalled();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
        });

        it('check isLoading propagates correctly', () => {
            (artemisIntelligenceService as any).isLoading = () => true;
            expect(comp.isCheckingConsistency()).toBeTrue();

            (artemisIntelligenceService as any).isLoading = () => false;
            expect(comp.isCheckingConsistency()).toBeFalse();
        });

        it('returns right icon', () => {
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.High)).toBe(faCircleExclamation);
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.Medium)).toBe(faTriangleExclamation);
            expect(comp.getSeverityIcon(ConsistencyIssue.SeverityEnum.Low)).toBe(faCircleInfo);
            expect(comp.getSeverityIcon(undefined as any)).toBe(faCircleInfo);
        });

        it('returns right color', () => {
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.High)).toBe('text-danger');
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.Medium)).toBe('text-warning');
            expect(comp.getSeverityColor(ConsistencyIssue.SeverityEnum.Low)).toBe('text-info');
            expect(comp.getSeverityColor(undefined as any)).toBe('text-secondary');
        });

        it('should toggle toolbar and select first issue if none selected', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.showConsistencyIssuesToolbar()).toBeTrue();

            const sorted = comp.sortedIssues();
            expect(comp.selectedIssue).toEqual(sorted[0]);
        });

        it('should navigate global next', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];

            const jumpSpy = jest.spyOn(comp as any, 'jumpToLocation').mockImplementation();

            // Next step
            comp.navigateGlobal(1);

            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(jumpSpy).toHaveBeenCalledWith(sorted[1]);

            comp.navigateGlobal(1);
            expect(comp.selectedIssue).toBe(sorted[2]);
        });

        it('should navigate global previous and wrap around', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];

            const jumpSpy = jest.spyOn(comp as any, 'jumpToLocation').mockImplementation();

            const lastIssue = sorted[sorted.length - 1];

            comp.navigateGlobal(-1);

            expect(comp.selectedIssue).toBe(lastIssue);
            expect(jumpSpy).toHaveBeenCalledWith(lastIssue);
        });

        it('navigates to PROBLEM_STATEMENT and calls jumpToLine', fakeAsync(() => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const issue = comp.sortedIssues().find((sortedIssue) => sortedIssue.targetType === CommentThreadLocationType.PROBLEM_STATEMENT)!;

            const jumpSpy = jest.spyOn((comp as any).editableInstructions, 'jumpToLine');

            (comp as any).jumpToLocation(issue);
            tick();

            expect((comp as any).codeEditorContainer.selectedFile).toBe('problem_statement.md');
            expect(jumpSpy).toHaveBeenCalledWith(issue.lineNumber);
        }));

        it('onEditorLoaded calls onFileLoad immediately when file is already selected', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            comp.fileToJumpOn = targetFile;
            (comp as any).codeEditorContainer.selectedFile = targetFile;

            const onFileLoadSpy = jest.spyOn(comp, 'onFileLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).toHaveBeenCalledWith(targetFile);
            expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
        });

        it('onEditorLoaded sets selectedFile when file is not selected yet', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            comp.fileToJumpOn = targetFile;
            (comp as any).codeEditorContainer.selectedFile = 'some/other/file.java';

            const onFileLoadSpy = jest.spyOn(comp, 'onFileLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).not.toHaveBeenCalled();
            expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
        });

        it('onEditorLoaded keeps deferred jump state until onFileLoad is called', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            const targetLine = 42;
            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;
            (comp as any).codeEditorContainer.selectedFile = 'some/other/file.java';

            comp.onEditorLoaded();

            expect((comp as any).codeEditorContainer.selectedFile).toBe(targetFile);
            expect(comp.fileToJumpOn).toBe(targetFile);
            expect(comp.lineJumpOnFileLoad).toBe(targetLine);

            comp.onFileLoad(targetFile);

            expect((comp as any).codeEditorContainer.jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onFileLoad jumps to line and clears lineJumpOnFileLoad when file matches', () => {
            const targetFile = 'src/solution/Solution.java';
            const targetLine = 60;

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;

            comp.onFileLoad(targetFile);

            expect((comp as any).codeEditorContainer.jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onFileLoad does nothing if file does not match fileToJumpOn', () => {
            comp.fileToJumpOn = 'src/solution/Solution.java';
            comp.lineJumpOnFileLoad = 60;

            comp.onFileLoad('src/tests/ExampleTest.java');

            expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBe(60);
        });

        it('onFileLoad does nothing if lineJumpOnFileLoad is undefined', () => {
            const targetFile = 'src/solution/Solution.java';

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = undefined;

            comp.onFileLoad(targetFile);

            expect((comp as any).codeEditorContainer.jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
        });

        it('shows error and clears jump state when repository selection fails', () => {
            const issue = {
                targetType: CommentThreadLocationType.TEST_REPO,
                filePath: 'src/tests/ExampleTest.java',
                lineNumber: 70,
            };
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue('SOLUTION');

            const error = new Error('repo selection failed');
            jest.spyOn(comp, 'selectTestRepository').mockImplementation(() => {
                throw error;
            });

            const alertErrorSpy = jest.spyOn(alertService, 'error');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).jumpToLocation(issue);

            expect(alertErrorSpy).toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects template repo when target is TEMPLATE_REPO and current repo differs', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.SOLUTION);
            const selectTemplateSpy = jest.spyOn(comp, 'selectTemplateParticipation');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({ targetType: CommentThreadLocationType.TEMPLATE_REPO, filePath: 'src/template/A.java', lineNumber: 10 });

            expect(selectTemplateSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects solution repo when target is SOLUTION_REPO and current repo differs', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.TEMPLATE);
            const selectSolutionSpy = jest.spyOn(comp, 'selectSolutionParticipation');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({ targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/solution/B.java', lineNumber: 11 });

            expect(selectSolutionSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects test repo when target is TEST_REPO and current repo differs', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.SOLUTION);
            const selectTestSpy = jest.spyOn(comp, 'selectTestRepository');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({ targetType: CommentThreadLocationType.TEST_REPO, filePath: 'src/test/C.java', lineNumber: 12 });

            expect(selectTestSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when target is AUXILIARY_REPO and current repo differs', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.TEMPLATE);
            comp.selectAuxiliaryRepository = jest.fn();
            const selectAuxSpy = jest.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 77,
                filePath: 'src/aux/D.java',
                lineNumber: 13,
            });

            expect(selectAuxSpy).toHaveBeenCalledWith(77);
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when already in AUXILIARY but repository id differs', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.AUXILIARY);
            comp.selectedRepositoryId = 12;
            comp.selectAuxiliaryRepository = jest.fn();
            const selectAuxSpy = jest.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 77,
                filePath: 'src/aux/D.java',
                lineNumber: 13,
            });

            expect(selectAuxSpy).toHaveBeenCalledWith(77);
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when auxiliaryRepositoryId is 0', () => {
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue(RepositoryType.TEMPLATE);
            comp.selectAuxiliaryRepository = jest.fn();
            const selectAuxSpy = jest.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).navigateToLocation({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 0,
                filePath: 'src/aux/D.java',
                lineNumber: 13,
            });

            expect(selectAuxSpy).toHaveBeenCalledWith(0);
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('should reset showConsistencyIssuesToolbar when re-running consistency check', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            (comp as any).showConsistencyIssuesToolbar.set(true);
            comp.selectedIssue = comp.sortedIssues()[0];

            jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            jest.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);

            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();
            expect(comp.selectedIssue).toBeUndefined();
        });
    });
});
