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
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';

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

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch (error) {
            // already initialized in some runners
        }
    });

    beforeEach(async () => {
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
            ],
        })
            // Avoid rendering heavy template dependencies for these tests
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
                set: { template: '', imports: [] as any },
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
        };

        (comp as any).editableInstructions = {
            jumpToLine: jest.fn(),
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
            comp.consistencyIssues.set(mockIssues);
            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.showConsistencyIssuesToolbar()).toBeTrue();
            expect(comp.selectedIssue).toBe(mockIssues[0]);

            const sorted = comp.sortedIssues();
            expect(comp.selectedIssue).toBe(sorted[0]);
            expect(comp.locationIndex).toBe(0);
        });

        it('should navigate global next', () => {
            comp.consistencyIssues.set(mockIssues);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];
            comp.locationIndex = 0;

            const jumpSpy = jest.spyOn(comp as any, 'jumpToLocation').mockImplementation();

            // Next step
            comp.navigateGlobal(1);

            // Should go to sorted[1] (Issue 4), locIndex 0
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(0);
            expect(jumpSpy).toHaveBeenCalledWith(sorted[1], 0);

            // Next step (Issue 4 has 3 locations)
            comp.navigateGlobal(1);
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(1);

            comp.navigateGlobal(1);
            expect(comp.selectedIssue).toBe(sorted[1]);
            expect(comp.locationIndex).toBe(2);

            comp.navigateGlobal(1);
            // Should go to sorted[2] (Issue 1), locIndex 0
            expect(comp.selectedIssue).toBe(sorted[2]);
            expect(comp.locationIndex).toBe(0);
        });

        it('should navigate global previous and wrap around', () => {
            comp.consistencyIssues.set(mockIssues);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];
            comp.locationIndex = 0;

            const jumpSpy = jest.spyOn(comp as any, 'jumpToLocation').mockImplementation();
          
            // Previous step -> Wrap to last issue, last location
            // Last issue is sorted[4] (Issue 3 Low), 1 location.
            const lastIssue = sorted[sorted.length - 1];
            // Last issue has 1 location.
            const lastLocIndex = lastIssue.relatedLocations.length - 1;

            comp.navigateGlobal(-1);

            expect(comp.selectedIssue).toBe(lastIssue);
            expect(comp.locationIndex).toBe(lastLocIndex);
            expect(jumpSpy).toHaveBeenCalledWith(lastIssue, lastLocIndex);
        });
          

describe('CodeEditorInstructorAndEditorContainerComponent - Review Comments', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let reviewService: jest.Mocked<
        Pick<
            ExerciseReviewCommentService,
            | 'deleteComment'
            | 'createUserComment'
            | 'updateUserCommentContent'
            | 'updateThreadResolvedState'
            | 'createThread'
            | 'removeCommentFromThreads'
            | 'appendCommentToThreads'
            | 'updateCommentInThreads'
            | 'replaceThreadInThreads'
            | 'appendThreadToThreads'
        >
    >;
    let alertService: AlertService;

    beforeEach(async () => {
        reviewService = {
            deleteComment: jest.fn(),
            createUserComment: jest.fn(),
            updateUserCommentContent: jest.fn(),
            updateThreadResolvedState: jest.fn(),
            createThread: jest.fn(),
            removeCommentFromThreads: jest.fn(),
            appendCommentToThreads: jest.fn(),
            updateCommentInThreads: jest.fn(),
            replaceThreadInThreads: jest.fn(),
            appendThreadToThreads: jest.fn(),
        };

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
                { provide: ExerciseReviewCommentService, useValue: reviewService },
            ],
        })
            .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
                set: { template: '', imports: [] as any },
            })
            .compileComponents();

        alertService = TestBed.inject(AlertService);
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = { id: 1 } as any;
    });

    afterEach(() => {
        fixture?.destroy();
        jest.clearAllMocks();
    });

    it('should delete a review comment and update threads', () => {
        const updatedThreads = [{ id: 2 }] as any;
        reviewService.deleteComment.mockReturnValue(of({} as any));
        reviewService.removeCommentFromThreads.mockReturnValue(updatedThreads);
        comp.reviewCommentThreads.set([{ id: 1 }] as any);

        comp.onDeleteReviewComment(5);

        expect(reviewService.deleteComment).toHaveBeenCalledWith(1, 5);
        expect(reviewService.removeCommentFromThreads).toHaveBeenCalledWith([{ id: 1 }], 5);
        expect(comp.reviewCommentThreads()).toEqual(updatedThreads);
    });

    it('should show an error when delete fails', () => {
        reviewService.deleteComment.mockReturnValue(throwError(() => new Error('fail')));
        const errorSpy = jest.spyOn(alertService, 'error');

        comp.onDeleteReviewComment(5);

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.review.deleteFailed');
    });

    it('should create a reply and append it to threads', () => {
        const createdComment = { id: 7, threadId: 9, type: 'USER', content: { contentType: 'USER', text: 'reply' } } as any;
        const updatedThreads = [{ id: 9, comments: [createdComment] }] as any;
        reviewService.createUserComment.mockReturnValue(of({ body: createdComment } as any));
        reviewService.appendCommentToThreads.mockReturnValue(updatedThreads);

        comp.onReplyReviewComment({ threadId: 9, text: 'reply' });

        expect(reviewService.createUserComment).toHaveBeenCalledWith(1, 9, expect.objectContaining({ contentType: 'USER', text: 'reply' }));
        expect(reviewService.appendCommentToThreads).toHaveBeenCalledWith([], createdComment);
        expect(comp.reviewCommentThreads()).toEqual(updatedThreads);
    });

    it('should show an error when reply fails', () => {
        reviewService.createUserComment.mockReturnValue(throwError(() => new Error('fail')));
        const errorSpy = jest.spyOn(alertService, 'error');

        comp.onReplyReviewComment({ threadId: 9, text: 'reply' });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.review.saveFailed');
    });

    it('should update a comment and update threads', () => {
        const updatedComment = { id: 7, threadId: 9, type: 'USER', content: { contentType: 'USER', text: 'updated' } } as any;
        const updatedThreads = [{ id: 9, comments: [updatedComment] }] as any;
        reviewService.updateUserCommentContent.mockReturnValue(of({ body: updatedComment } as any));
        reviewService.updateCommentInThreads.mockReturnValue(updatedThreads);

        comp.onUpdateReviewComment({ commentId: 7, text: 'updated' });

        expect(reviewService.updateUserCommentContent).toHaveBeenCalledWith(1, 7, { contentType: 'USER', text: 'updated' });
        expect(reviewService.updateCommentInThreads).toHaveBeenCalledWith([], updatedComment);
        expect(comp.reviewCommentThreads()).toEqual(updatedThreads);
    });

    it('should show an error when update fails', () => {
        reviewService.updateUserCommentContent.mockReturnValue(throwError(() => new Error('fail')));
        const errorSpy = jest.spyOn(alertService, 'error');

        comp.onUpdateReviewComment({ commentId: 7, text: 'updated' });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.review.saveFailed');
    });

    it('should resolve a thread and update threads', () => {
        const updatedThread = { id: 3, resolved: true } as any;
        const updatedThreads = [{ id: 3, resolved: true }] as any;
        reviewService.updateThreadResolvedState.mockReturnValue(of({ body: updatedThread } as any));
        reviewService.replaceThreadInThreads.mockReturnValue(updatedThreads);

        comp.onToggleResolveReviewThread({ threadId: 3, resolved: true });

        expect(reviewService.updateThreadResolvedState).toHaveBeenCalledWith(1, 3, true);
        expect(reviewService.replaceThreadInThreads).toHaveBeenCalledWith([], updatedThread);
        expect(comp.reviewCommentThreads()).toEqual(updatedThreads);
    });

    it('should show an error when resolve fails', () => {
        reviewService.updateThreadResolvedState.mockReturnValue(throwError(() => new Error('fail')));
        const errorSpy = jest.spyOn(alertService, 'error');

        comp.onToggleResolveReviewThread({ threadId: 3, resolved: true });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.review.resolveFailed');
    });

    it('should create a thread for auxiliary repository', () => {
        const createdThread = { id: 11, comments: [] } as any;
        const updatedThreads = [{ id: 11 }] as any;
        reviewService.createThread.mockReturnValue(of({ body: createdThread } as any));
        reviewService.appendThreadToThreads.mockReturnValue(updatedThreads);
        comp.selectedRepository = RepositoryType.AUXILIARY;
        comp.selectedRepositoryId = 42;

        comp.onSubmitReviewComment({ lineNumber: 5, fileName: 'file.java', text: 'text' });

        expect(reviewService.createThread).toHaveBeenCalledWith(
            1,
            expect.objectContaining({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                initialFilePath: 'file.java',
                initialLineNumber: 5,
                auxiliaryRepositoryId: 42,
                initialComment: expect.objectContaining({ contentType: 'USER', text: 'text' }),
            }) as CreateCommentThread,
        );
        expect(reviewService.appendThreadToThreads).toHaveBeenCalledWith([], createdThread);
        expect(comp.reviewCommentThreads()).toEqual(updatedThreads);
    });

    it('should show an error when thread creation fails', () => {
        reviewService.createThread.mockReturnValue(throwError(() => new Error('fail')));
        const errorSpy = jest.spyOn(alertService, 'error');
        comp.selectedRepository = RepositoryType.TEMPLATE;

        comp.onSubmitReviewComment({ lineNumber: 5, fileName: 'file.java', text: 'text' });

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.review.saveFailed');
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Consistency Checks', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let alertService: AlertService;

        it('navigates to PROBLEM_STATEMENT and calls jumpToLine', fakeAsync(() => {
            // Mock issue with ProblemStatement
            const issue = mockIssues[0]; // ProblemStatement issue
            const loc = issue.relatedLocations[0];

            comp.selectedIssue = issue;
            comp.locationIndex = 0;

            const jumpSpy = jest.spyOn((comp as any).editableInstructions, 'jumpToLine');

            (comp as any).jumpToLocation(issue, 0); // Corrected: use (comp as any)
            tick();

            expect((comp as any).codeEditorContainer.selectedFile).toBe('problem_statement.md');
            expect(jumpSpy).toHaveBeenCalledWith(loc.endLine);
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
        });

        it('shows error and clears jump state when repository selection fails', () => {
            const issue = mockIssues[3]; // TESTS_REPOSITORY
            (comp as any).codeEditorContainer.selectedRepository = jest.fn().mockReturnValue('SOLUTION');

            const error = new Error('repo selection failed');
            jest.spyOn(comp, 'selectTestRepository').mockImplementation(() => {
                throw error;
            });

            const alertErrorSpy = jest.spyOn(alertService, 'error');
            const onEditorLoadedSpy = jest.spyOn(comp, 'onEditorLoaded');

            (comp as any).jumpToLocation(issue, 0);

            expect(alertErrorSpy).toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('should reset showConsistencyIssuesToolbar when re-running consistency check', () => {
            comp.consistencyIssues.set(mockIssues);
            (comp as any).showConsistencyIssuesToolbar.set(true);
            comp.selectedIssue = mockIssues[0];

            jest.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            jest.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ issues: [] } as ConsistencyCheckResponse));
            jest.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);

            expect(comp.showConsistencyIssuesToolbar()).toBeFalse();
            expect(comp.selectedIssue).toBeUndefined();
        });
    });
});
