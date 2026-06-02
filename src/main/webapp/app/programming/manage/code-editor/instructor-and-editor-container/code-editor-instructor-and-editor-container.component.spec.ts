import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// Mock y-monaco so MonacoBinding does not require a real Monaco editor instance.
// The mock exposes a controllable `destroy` spy that lets tests verify the
// double-destroy guard in the real createFileBinding implementation.
vi.mock('y-monaco', () => {
    const mockDestroy = vi.fn();
    // Use a real `function` (not an arrow) so the production code can invoke it with `new`.
    const MockMonacoBinding = vi.fn(function (this: any) {
        this.destroy = mockDestroy;
    });
    (MockMonacoBinding as any).__mockDestroy = mockDestroy;
    return { MonacoBinding: MockMonacoBinding };
});
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Provider, Signal, TemplateRef, WritableSignal, signal } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { FileSyncState } from 'app/exercise/synchronization/services/code-editor-file-sync.service';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
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
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';

import { HttpErrorResponse } from '@angular/common/http';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { ExerciseEditorSyncService } from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { DialogService } from 'primeng/dynamicdialog';

/**
 * Typed view onto the component's protected/private members so the spec can read and stub them
 * without a blanket `(component as any)` cast. The shape mirrors the relevant declarations.
 *
 * `codeEditorContainer` is a `viewChild` signal in the (now migrated) base container, so tests
 * stub it by assigning a callable that returns the container double. `editableInstructions` and
 * `codeGenerationStatusPopover` are equally `viewChild` signals.
 */
type ComponentInternalsOverrides = {
    codeEditorContainer: Signal<any>;
    editableInstructions: Signal<any>;
    codeGenerationRunningModal: Signal<any>;
    codeGenerationStatusPopover: Signal<any>;
    showConsistencyIssuesToolbar: WritableSignal<boolean>;
    fileSyncService: any;
    currentFileBinding: any;
    statusSubscription?: { unsubscribe: () => void };
    queuedCodeGenerationRepositories: RepositoryType[];
    activeCodeGenerationRepository?: RepositoryType;
    currentCodeGenerationUsesInitialIterationLimit: boolean;
    codeGenerationPullSubscriptions: Map<RepositoryType, { closed: boolean; unsubscribe: () => void }>;
    repositoriesWithInFlightCodeGenerationPull: Set<RepositoryType>;
    startCodeGeneration: (repositories: RepositoryType[], initialAutoGeneration?: boolean) => void;
    restoreCodeGenerationState: () => void;
    subscribeToJob: (jobId: string, repositoryType: RepositoryType) => void;
    clearJobSubscription: (stopSpinner: boolean) => void;
    clearCodeGenerationRepositoryPulls: () => void;
    initializeCodeGenerationRunStatuses: (repositories: RepositoryType[]) => void;
    updateCodeGenerationStatus: (repositoryType: RepositoryType, updater: (status: any) => any) => void;
    loadPersistedCodeGenerationState: () => unknown;
    applyDomainChange: (domainType: any, domainValue: any) => void;
    jumpToLocation: (issue: any) => void;
    navigateToLocation: (location: any) => void;
    onFileSyncLoad: (fileName: string) => void;
    createFileBinding: (syncState: any, model: any, editorInstance: any) => void;
    teardownFileBinding: () => void;
};
type ComponentInternals = Omit<CodeEditorInstructorAndEditorContainerComponent, keyof ComponentInternalsOverrides> & ComponentInternalsOverrides;
const internals = (c: CodeEditorInstructorAndEditorContainerComponent): ComponentInternals => c as unknown as ComponentInternals;

/** Stub shape for the code editor container double that the base container exposes via a viewChild signal. */
interface CodeEditorContainerStub {
    // actions and monacoEditor are themselves viewChild() signals on the container, so the stub
    // exposes them as callables that return the inner double (matching `.actions()` / `.monacoEditor()`).
    actions?: () => { executeRefresh: ReturnType<typeof vi.fn>; onSave: ReturnType<typeof vi.fn> };
    selectedFile?: string;
    selectedRepository?: ReturnType<typeof vi.fn>;
    problemStatementIdentifier?: string;
    jumpToLine?: ReturnType<typeof vi.fn>;
    initializeProperties?: ReturnType<typeof vi.fn>;
    monacoEditor?: () => any;
}

/** Assigns the codeEditorContainer viewChild stub via a callable, matching the migrated signal API. */
function setCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent, stub: CodeEditorContainerStub | undefined): void {
    internals(comp).codeEditorContainer = (() => stub) as unknown as Signal<any>;
}

/** Reads the codeEditorContainer viewChild stub. */
function getCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent): any {
    return internals(comp).codeEditorContainer();
}

/** Assigns the editableInstructions viewChild stub via a callable, matching the migrated signal API. */
function setEditableInstructions(comp: CodeEditorInstructorAndEditorContainerComponent, stub: any): void {
    internals(comp).editableInstructions = (() => stub) as unknown as Signal<any>;
}

/**
 * Builds the default code editor container double used across the suites.
 */
function createDefaultContainerStub(): CodeEditorContainerStub {
    const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
    const monacoEditor = { clearReviewCommentDrafts: vi.fn() };
    return {
        actions: () => actions,
        selectedFile: undefined as string | undefined,
        selectedRepository: vi.fn().mockReturnValue('SOLUTION'),
        problemStatementIdentifier: 'problem_statement.md',
        jumpToLine: vi.fn(),
        initializeProperties: vi.fn(),
        monacoEditor: () => monacoEditor,
    };
}

/**
 * Creates a typed mock ProgrammingExercise for testing.
 * @param overrides Partial properties to override the default values
 */
const AUTO_START_CODE_GENERATION_ALL_REPOSITORIES_STATE = 'autoStartCodeGenerationAllRepositories';

function createMockExercise(overrides: Partial<ProgrammingExercise> = {}): ProgrammingExercise {
    const mockCourse = new Course();
    mockCourse.id = 1;

    const exercise = new ProgrammingExercise(mockCourse, undefined);
    exercise.id = 42;
    exercise.problemStatement = 'Test problem statement';

    return Object.assign(exercise, overrides);
}

/**
 * Creates base providers shared across all test suites.
 * @param additionalProviders Optional providers to include with the base set
 */
function getBaseProviders(additionalProviders: Provider[] = []): Provider[] {
    return [
        { provide: AlertService, useClass: MockAlertService },
        { provide: ProfileService, useClass: MockProfileService },
        { provide: Router, useClass: MockRouter },
        { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
        { provide: CourseExerciseService, useClass: MockCourseExerciseService },
        { provide: DomainService, useValue: { setDomain: vi.fn() } },
        { provide: Location, useValue: { replaceState: vi.fn() } },
        { provide: ParticipationService, useClass: MockParticipationService },
        { provide: ActivatedRoute, useValue: { params: of({}) } },
        { provide: HyperionCodeGenerationApiService, useValue: { generateCode: vi.fn() } },
        { provide: NgbModal, useValue: { open: vi.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
        { provide: DialogService, useValue: { open: vi.fn(() => ({ onClose: of({ confirmed: true }) })) } },
        { provide: HyperionWebsocketService, useValue: { subscribeToJob: vi.fn(), unsubscribeFromJob: vi.fn() } },
        { provide: CodeEditorRepositoryService, useValue: { pull: vi.fn(() => of(void 0)) } },
        { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: vi.fn(() => of({})) } },
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: vi.fn() } },
        { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: vi.fn(), isLoading: () => false } },
        { provide: ExerciseEditorSyncService, useValue: { connect: vi.fn(), disconnect: vi.fn(), subscribeToUpdates: vi.fn(() => of()) } },
        ...additionalProviders,
    ];
}

/**
 * Configures TestBed with the standard component setup.
 * @param additionalProviders Optional providers to include with the base set
 */
async function configureTestBed(additionalProviders: Provider[] = []): Promise<void> {
    // Extract the ExerciseReviewCommentService mock from additional providers (if provided)
    // to also override it at the component level, since the component declares its own provider.
    const reviewCommentProvider = additionalProviders.find((p: any) => p.provide === ExerciseReviewCommentService);
    const componentProviders = reviewCommentProvider ? [reviewCommentProvider] : [];

    await TestBed.configureTestingModule({
        imports: [CodeEditorInstructorAndEditorContainerComponent],
        providers: getBaseProviders(additionalProviders),
    })
        .overrideComponent(CodeEditorInstructorAndEditorContainerComponent, {
            set: { template: '', imports: [], providers: componentProviders },
        })
        .compileComponents();
}

describe('CodeEditorInstructorAndEditorContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let codeGenerationApi: { generateCode: ReturnType<typeof vi.fn> };
    let ws: { subscribeToJob: ReturnType<typeof vi.fn>; unsubscribeFromJob: ReturnType<typeof vi.fn> };
    let alertService: AlertService;
    let repoService: CodeEditorRepositoryService;
    let profileService: ProfileService;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let sessionStorageService: SessionStorageService;
    let reviewCommentService: {
        setExercise: ReturnType<typeof vi.fn>;
        reloadThreads: ReturnType<typeof vi.fn>;
        getSelectedFeedbackThreadIdsForRepository: ReturnType<typeof vi.fn>;
        threads: WritableSignal<any[]>;
    };

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

    beforeEach(async () => {
        reviewCommentService = {
            setExercise: vi.fn(),
            reloadThreads: vi.fn(),
            getSelectedFeedbackThreadIdsForRepository: vi.fn(() => []),
            threads: signal([]),
        };
        reviewCommentService.reloadThreads.mockImplementation((onLoaded?: () => void) => onLoaded?.());

        await configureTestBed([{ provide: ExerciseReviewCommentService, useValue: reviewCommentService }]);

        alertService = TestBed.inject(AlertService);
        codeGenerationApi = TestBed.inject(HyperionCodeGenerationApiService) as unknown as { generateCode: ReturnType<typeof vi.fn> };
        ws = TestBed.inject(HyperionWebsocketService) as unknown as { subscribeToJob: ReturnType<typeof vi.fn>; unsubscribeFromJob: ReturnType<typeof vi.fn> };
        profileService = TestBed.inject(ProfileService);
        repoService = TestBed.inject(CodeEditorRepositoryService);
        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);
        sessionStorageService = TestBed.inject(SessionStorageService);
        sessionStorageService.clear();

        // Enable Hyperion by default so property initialization is deterministic
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        // Minimal exercise setup used by generateCode
        comp.exercise = createMockExercise();

        // Mock codeEditorContainer (viewChild signal) and editableInstructions (viewChild signal)
        setCodeEditorContainer(comp, createDefaultContainerStub());
        setEditableInstructions(comp, {
            jumpToLine: vi.fn(),
            clearReviewCommentDrafts: vi.fn(),
        });
        // codeGenerationRunningModal is a required viewChild() referencing an <ng-template>; the
        // template-less spec never resolves it, so stub the signal to avoid NG0951 on access.
        internals(comp).codeGenerationRunningModal = (() => ({}) as TemplateRef<unknown>) as unknown as Signal<any>;

        // Mock jump helper methods
        comp.selectTemplateParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectSolutionParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectTestRepository = vi.fn().mockResolvedValue(undefined);
    });

    afterEach(() => {
        window.history.replaceState({}, '', window.location.href);
        sessionStorageService?.clear();
        fixture?.destroy();
        vi.clearAllMocks();
    });

    describe('Code Generation', () => {
        const selectCodeGenerationRepositories = (...repositories: RepositoryType[]) => {
            comp.setCodeGenerationRepositoryEnabled(RepositoryType.TEMPLATE, repositories.includes(RepositoryType.TEMPLATE));
            comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, repositories.includes(RepositoryType.SOLUTION));
            comp.setCodeGenerationRepositoryEnabled(RepositoryType.TESTS, repositories.includes(RepositoryType.TESTS));
        };

        it('should not generate when no exercise id', async () => {
            comp.exercise = undefined as any;
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);

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

        it('should warn when no repository is selected', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.ASSIGNMENT;

            comp.generateCode();

            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.noRepositorySelected' }),
            );
        });

        it('should call API, subscribe, and show success alert on DONE success', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);

            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-1' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve(); // resolve modal

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE, checkOnly: false });

            // Emit DONE success event
            job$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS' });

            expect(comp.isGeneratingCode()).toBe(false);
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.SUCCESS,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                    translationParams: { repositoryType: RepositoryType.TEMPLATE },
                }),
            );
        });

        it('should mark the repository as warning and preserve the message on DONE partial completion', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;
            selectCodeGenerationRepositories(RepositoryType.SOLUTION);

            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-2' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.SOLUTION, checkOnly: false });

            job$.next({ type: 'DONE', success: false, completionStatus: 'PARTIAL', message: 'Generation completed, but the build failed' });

            expect(comp.isGeneratingCode()).toBe(false);
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.SOLUTION)).toEqual(
                expect.objectContaining({
                    state: 'warning',
                    message: 'Generation completed, but the build failed',
                }),
            );
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.warning',
                    translationParams: { repositoryType: RepositoryType.SOLUTION },
                }),
            );
        });

        it('should mark the repository as error and show error alert on DONE failure', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;
            selectCodeGenerationRepositories(RepositoryType.SOLUTION);

            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-2b' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'DONE', success: false, completionStatus: 'ERROR', message: 'Generation finished without producing files' });

            expect(comp.isGeneratingCode()).toBe(false);
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.SOLUTION)).toEqual(
                expect.objectContaining({
                    state: 'error',
                    message: 'Generation finished without producing files',
                }),
            );
            expect(addAlertSpy).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: RepositoryType.SOLUTION },
                }),
            );
        });

        it('should show error alert on API error', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;
            selectCodeGenerationRepositories(RepositoryType.TESTS);

            codeGenerationApi.generateCode.mockReturnValue(throwError(() => new Error('fail')) as any);

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TESTS, checkOnly: false });
            expect(comp.isGeneratingCode()).toBe(false);
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
            expect(comp.hyperionEnabled).toBe(true);
        });

        it('should compute hyperionEnabled as false when feature disabled', () => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            // Recreate the component so the property initializer runs with the new spy value
            fixture.destroy();
            fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
            comp = fixture.componentInstance;

            expect(comp.hyperionEnabled).toBe(false);
        });

        it('should debounce repository pulls across FILE_UPDATED and NEW_FILE events', async () => {
            vi.useFakeTimers();
            try {
                comp.selectedRepository = RepositoryType.TEMPLATE;
                selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
                codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-3' }));

                const job$ = new Subject<any>();
                ws.subscribeToJob.mockReturnValue(job$.asObservable());
                const pullSpy = vi.spyOn(repoService, 'pull');

                comp.generateCode();
                await vi.advanceTimersByTimeAsync(0);

                job$.next({ type: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 1 });
                job$.next({ type: 'NEW_FILE', path: 'src/test/java/AppTest.java', iteration: 2 });

                expect(pullSpy).not.toHaveBeenCalled();

                await vi.advanceTimersByTimeAsync(250);

                expect(pullSpy).toHaveBeenCalledOnce();
                expect(comp.codeGenerationActivityLog()).toEqual([
                    expect.objectContaining({ repositoryType: RepositoryType.TEMPLATE, eventType: 'NEW_FILE', path: 'src/test/java/AppTest.java', iteration: 2 }),
                    expect.objectContaining({ repositoryType: RepositoryType.TEMPLATE, eventType: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 1 }),
                ]);
                expect(
                    comp.getCodeGenerationIterationActivityGroups(
                        comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)!.fileActivities,
                    ),
                ).toEqual([
                    {
                        iteration: 2,
                        activities: [expect.objectContaining({ path: 'src/test/java/AppTest.java', iteration: 2 })],
                    },
                    {
                        iteration: 1,
                        activities: [expect.objectContaining({ path: 'src/main/java/App.java', iteration: 1 })],
                    },
                ]);
            } finally {
                vi.useRealTimers();
            }
        });

        it('should flush a pending repository pull when DONE arrives before the debounce window elapses', async () => {
            vi.useFakeTimers();
            try {
                comp.selectedRepository = RepositoryType.TEMPLATE;
                selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
                codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-3' }));

                const job$ = new Subject<any>();
                ws.subscribeToJob.mockReturnValue(job$.asObservable());
                const pullSpy = vi.spyOn(repoService, 'pull');

                comp.generateCode();
                await vi.advanceTimersByTimeAsync(0);

                job$.next({ type: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 1 });
                expect(pullSpy).not.toHaveBeenCalled();

                job$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
                await vi.advanceTimersByTimeAsync(0);

                expect(pullSpy).toHaveBeenCalledOnce();

                await vi.advanceTimersByTimeAsync(250);

                expect(pullSpy).toHaveBeenCalledOnce();
            } finally {
                vi.useRealTimers();
            }
        });

        it('should keep the final repository pull alive on DONE until the pull completes', async () => {
            vi.useFakeTimers();
            try {
                comp.selectedRepository = RepositoryType.TEMPLATE;
                selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
                codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-3b' }));

                const job$ = new Subject<any>();
                const pull$ = new Subject<void>();
                ws.subscribeToJob.mockReturnValue(job$.asObservable());
                vi.spyOn(repoService, 'pull').mockReturnValue(pull$.asObservable());

                comp.generateCode();
                await vi.advanceTimersByTimeAsync(0);

                job$.next({ type: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 1 });
                await vi.advanceTimersByTimeAsync(250);

                const trackedPullSubscription = internals(comp).codeGenerationPullSubscriptions.get(RepositoryType.TEMPLATE);
                expect(trackedPullSubscription?.closed).toBe(false);

                job$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
                await vi.advanceTimersByTimeAsync(0);

                expect(comp.isGeneratingCode()).toBe(false);
                expect(trackedPullSubscription?.closed).toBe(false);
                expect(internals(comp).codeGenerationPullSubscriptions.get(RepositoryType.TEMPLATE)).toBe(trackedPullSubscription);

                pull$.complete();
                await vi.advanceTimersByTimeAsync(0);

                expect(internals(comp).codeGenerationPullSubscriptions.has(RepositoryType.TEMPLATE)).toBe(false);
            } finally {
                vi.useRealTimers();
            }
        });

        it('should unsubscribe in-flight repository pulls during cleanup', async () => {
            vi.useFakeTimers();
            try {
                comp.selectedRepository = RepositoryType.TEMPLATE;
                selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
                codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-3' }));

                const job$ = new Subject<any>();
                const pull$ = new Subject<void>();
                ws.subscribeToJob.mockReturnValue(job$.asObservable());
                vi.spyOn(repoService, 'pull').mockReturnValue(pull$.asObservable());

                comp.generateCode();
                await vi.advanceTimersByTimeAsync(0);

                job$.next({ type: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 1 });
                await vi.advanceTimersByTimeAsync(250);

                const trackedPullSubscription = internals(comp).codeGenerationPullSubscriptions.get(RepositoryType.TEMPLATE);
                expect(trackedPullSubscription?.closed).toBe(false);

                internals(comp).clearCodeGenerationRepositoryPulls();

                expect(trackedPullSubscription?.closed).toBe(true);
                expect(internals(comp).codeGenerationPullSubscriptions.size).toBe(0);
                expect(internals(comp).repositoriesWithInFlightCodeGenerationPull.size).toBe(0);
            } finally {
                vi.useRealTimers();
            }
        });

        it('should not pull the repository when file events belong to a non-selected repository', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-template' }));

            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());
            const pullSpy = vi.spyOn(repoService, 'pull');

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 2 });

            expect(pullSpy).not.toHaveBeenCalled();
            expect(comp.codeGenerationActivityLog()).toEqual([
                expect.objectContaining({ repositoryType: RepositoryType.TEMPLATE, eventType: 'FILE_UPDATED', path: 'src/main/java/App.java', iteration: 2 }),
            ]);
        });

        it('should show modal when code generation is already running', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            const modalService = TestBed.inject(NgbModal);
            const openSpy = vi.spyOn(modalService, 'open');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);

            const conflict = new HttpErrorResponse({
                status: 409,
                error: { 'X-artemisApp-error': 'error.codeGenerationRunning' },
            });
            codeGenerationApi.generateCode.mockReturnValue(throwError(() => conflict) as any);

            comp.generateCode();
            await Promise.resolve();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE, checkOnly: false });
            expect(comp.isGeneratingCode()).toBe(false);
            expect(openSpy).toHaveBeenCalledOnce();
            expect(addAlertSpy).not.toHaveBeenCalledWith(
                expect.objectContaining({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                }),
            );
        });

        it('should call executeRefresh and cleanup on DONE', async () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            selectCodeGenerationRepositories(RepositoryType.SOLUTION);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-4' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            const executeRefresh = getCodeEditorContainer(comp).actions().executeRefresh;

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS' });
            await Promise.resolve();

            expect(executeRefresh).toHaveBeenCalled();
            expect(comp.isGeneratingCode()).toBe(false);
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-4');
        });

        it('should show danger alert and cleanup on ERROR event', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-5' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'ERROR' });
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBe(false);
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-5');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should mark later repositories as skipped when the active repository errors', async () => {
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE, RepositoryType.SOLUTION);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-template' }));
            const job$ = new Subject<any>();
            ws.subscribeToJob.mockReturnValue(job$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            job$.next({ type: 'ERROR', message: 'Template generation failed' });

            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.SOLUTION)).toEqual(
                expect.objectContaining({
                    state: 'error',
                    message: 'Template generation failed',
                }),
            );
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)).toEqual(
                expect.objectContaining({
                    state: 'skipped',
                    message: 'artemisApp.programmingExercise.codeGeneration.status.skippedMessage',
                }),
            );
        });

        it('should show danger alert and cleanup when job stream errors', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TESTS;
            selectCodeGenerationRepositories(RepositoryType.TESTS);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-6' }));
            ws.subscribeToJob.mockReturnValue(throwError(() => new Error('ws')));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBe(false);
            expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-6');
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show danger alert when response has no job id', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE);
            codeGenerationApi.generateCode.mockReturnValue(of({}));

            comp.generateCode();
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBe(false);
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ type: AlertType.DANGER, translationKey: 'artemisApp.programmingExercise.codeGeneration.error' }));
        });

        it('should show timeout warning and cleanup when generation exceeds time limit', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.SOLUTION;
            selectCodeGenerationRepositories(RepositoryType.SOLUTION);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-7' }));

            // Intercept setTimeout to capture the scheduled callback and invoke it immediately
            const originalSetTimeout = window.setTimeout;
            let timeoutCallback: (() => void) | undefined;
            // @ts-ignore
            window.setTimeout = ((fn: () => void, _delay?: number) => {
                timeoutCallback = fn;
                return 1 as any;
            }) as any;

            try {
                ws.subscribeToJob.mockReturnValue(new Subject<any>().asObservable());
                comp.generateCode();
                await Promise.resolve();
                expect(comp.isGeneratingCode()).toBe(true);

                // Simulate timeout
                if (timeoutCallback) {
                    timeoutCallback();
                }

                expect(comp.isGeneratingCode()).toBe(false);
                expect(ws.unsubscribeFromJob).toHaveBeenCalledWith('job-7');
                expect(addAlertSpy).toHaveBeenCalledWith(
                    expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }),
                );
            } finally {
                window.setTimeout = originalSetTimeout;
            }
        });

        it('should preserve the timed-out repository error state when that repository is still present in the queue', async () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            comp.selectedRepository = RepositoryType.TEMPLATE;
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE, RepositoryType.SOLUTION);
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-timeout' }));

            const originalSetTimeout = window.setTimeout;
            let timeoutCallback: (() => void) | undefined;
            // @ts-ignore
            window.setTimeout = ((fn: () => void, _delay?: number) => {
                timeoutCallback = fn;
                return 1 as any;
            }) as any;

            try {
                ws.subscribeToJob.mockReturnValue(new Subject<any>().asObservable());
                comp.generateCode();
                await Promise.resolve();

                // Simulate stale queue bookkeeping where the active repository is still present.
                internals(comp).queuedCodeGenerationRepositories = [RepositoryType.TEMPLATE, RepositoryType.SOLUTION];

                if (timeoutCallback) {
                    timeoutCallback();
                }

                expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.SOLUTION)).toEqual(
                    expect.objectContaining({
                        state: 'error',
                        message: 'artemisApp.programmingExercise.codeGeneration.timeoutDetails',
                    }),
                );
                expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)).toEqual(
                    expect.objectContaining({
                        state: 'skipped',
                        message: 'artemisApp.programmingExercise.codeGeneration.status.skippedMessage',
                    }),
                );
                expect(addAlertSpy).toHaveBeenCalledWith(
                    expect.objectContaining({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout' }),
                );
            } finally {
                window.setTimeout = originalSetTimeout;
            }
        });

        it('should generate selected repositories consecutively in solution-template-test order', async () => {
            selectCodeGenerationRepositories(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);

            const solutionJob$ = new Subject<any>();
            const templateJob$ = new Subject<any>();
            const testsJob$ = new Subject<any>();
            codeGenerationApi.generateCode
                .mockReturnValueOnce(of({ jobId: 'job-solution' }))
                .mockReturnValueOnce(of({}))
                .mockReturnValueOnce(of({ jobId: 'job-template' }))
                .mockReturnValueOnce(of({}))
                .mockReturnValueOnce(of({ jobId: 'job-tests' }));
            ws.subscribeToJob.mockReturnValueOnce(solutionJob$.asObservable()).mockReturnValueOnce(templateJob$.asObservable()).mockReturnValueOnce(testsJob$.asObservable());

            comp.generateCode();
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(1, 42, { repositoryType: RepositoryType.SOLUTION, checkOnly: false });

            solutionJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(2, 42, { checkOnly: true });
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(3, 42, { repositoryType: RepositoryType.TEMPLATE, checkOnly: false });

            templateJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
            await Promise.resolve();
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(4, 42, { checkOnly: true });
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(5, 42, { repositoryType: RepositoryType.TESTS, checkOnly: false });

            testsJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
            await Promise.resolve();

            expect(comp.isGeneratingCode()).toBe(false);
            expect(comp.codeGenerationStatuses().map((status) => [status.repositoryType, status.state])).toEqual([
                [RepositoryType.SOLUTION, 'success'],
                [RepositoryType.TEMPLATE, 'success'],
                [RepositoryType.TESTS, 'success'],
            ]);
        });

        it('should auto-start generation for all three repositories when requested by navigation state', () => {
            window.history.replaceState({ [AUTO_START_CODE_GENERATION_ALL_REPOSITORIES_STATE]: true }, '', window.location.href);

            const route = TestBed.inject(ActivatedRoute) as any;
            route.params = of({ exerciseId: '42', repositoryType: RepositoryType.SOLUTION, repositoryId: '2' });

            fixture.destroy();
            fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
            comp = fixture.componentInstance;
            comp.exercise = createMockExercise({
                templateParticipation: { id: 1, repositoryUri: 'template-repository' } as any,
                solutionParticipation: { id: 2, repositoryUri: 'solution-repository' } as any,
            });

            setCodeEditorContainer(comp, createDefaultContainerStub());
            setEditableInstructions(comp, {
                jumpToLine: vi.fn(),
                clearReviewCommentDrafts: vi.fn(),
            });

            const startCodeGenerationSpy = vi.spyOn(internals(comp), 'startCodeGeneration').mockImplementation(() => {});
            codeGenerationApi.generateCode.mockReturnValue(of({}));

            fixture.detectChanges();
            internals(comp).applyDomainChange(DomainType.PARTICIPATION, comp.exercise.solutionParticipation);
            fixture.detectChanges();
            fixture.detectChanges();

            expect(startCodeGenerationSpy).toHaveBeenCalledOnce();
            expect(startCodeGenerationSpy).toHaveBeenCalledWith([RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS], true);
        });

        it('should not auto-start generation from navigation state while generation is already running', () => {
            window.history.replaceState({ [AUTO_START_CODE_GENERATION_ALL_REPOSITORIES_STATE]: true }, '', window.location.href);

            const route = TestBed.inject(ActivatedRoute) as any;
            route.params = of({ exerciseId: '42', repositoryType: RepositoryType.SOLUTION, repositoryId: '2' });

            fixture.destroy();
            fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
            comp = fixture.componentInstance;
            comp.exercise = createMockExercise();
            comp.isGeneratingCode.set(true);

            setCodeEditorContainer(comp, createDefaultContainerStub());
            setEditableInstructions(comp, {
                jumpToLine: vi.fn(),
                clearReviewCommentDrafts: vi.fn(),
            });

            const startCodeGenerationSpy = vi.spyOn(internals(comp), 'startCodeGeneration').mockImplementation(() => {});
            const replaceStateSpy = vi.spyOn(window.history, 'replaceState');

            fixture.detectChanges();
            internals(comp).applyDomainChange(DomainType.PARTICIPATION, comp.exercise.solutionParticipation);

            expect(startCodeGenerationSpy).not.toHaveBeenCalled();
            expect(replaceStateSpy).not.toHaveBeenCalled();
        });

        it('should mark auto-started generation requests as initial auto generation', async () => {
            const solutionJob$ = new Subject<any>();
            codeGenerationApi.generateCode.mockReturnValueOnce(of({ jobId: 'job-solution' }));
            ws.subscribeToJob.mockReturnValueOnce(solutionJob$.asObservable());

            internals(comp).startCodeGeneration([RepositoryType.SOLUTION], true);
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, {
                repositoryType: RepositoryType.SOLUTION,
                checkOnly: false,
                initialAutoGeneration: true,
            });
        });

        it('should wait until the previous job slot is released before starting the next repository', async () => {
            vi.useFakeTimers();
            try {
                selectCodeGenerationRepositories(RepositoryType.TEMPLATE, RepositoryType.SOLUTION);

                const solutionJob$ = new Subject<any>();
                const templateJob$ = new Subject<any>();
                codeGenerationApi.generateCode
                    .mockReturnValueOnce(of({ jobId: 'job-solution' }))
                    .mockReturnValueOnce(of({ jobId: 'job-solution-still-active' }))
                    .mockReturnValueOnce(of({}))
                    .mockReturnValueOnce(of({ jobId: 'job-template' }));
                ws.subscribeToJob.mockReturnValueOnce(solutionJob$.asObservable()).mockReturnValueOnce(templateJob$.asObservable());

                comp.generateCode();
                await vi.advanceTimersByTimeAsync(0);

                solutionJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
                await vi.advanceTimersByTimeAsync(0);

                expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(2, 42, { checkOnly: true });
                expect(codeGenerationApi.generateCode).toHaveBeenCalledTimes(2);

                await vi.advanceTimersByTimeAsync(1000);

                expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(3, 42, { checkOnly: true });
                expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(4, 42, { repositoryType: RepositoryType.TEMPLATE, checkOnly: false });

                templateJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
                await vi.advanceTimersByTimeAsync(0);

                expect(comp.isGeneratingCode()).toBe(false);
            } finally {
                vi.useRealTimers();
            }
        });

        it('should realign the code generation status popover when status content changes while visible', async () => {
            vi.useFakeTimers();
            try {
                const align = vi.fn();
                const container = document.createElement('div');
                container.style.insetInlineStart = '';
                const setPropertySpy = vi.spyOn(container.style, 'setProperty');
                Object.defineProperty(container, 'getBoundingClientRect', {
                    value: () => ({ left: 80, width: 200 }),
                });
                vi.spyOn(comp, 'codeGenerationStatusPopover').mockReturnValue({
                    overlayVisible: true,
                    align,
                    target: {
                        getBoundingClientRect: () => ({ left: 120, width: 20 }),
                        querySelector: () =>
                            ({
                                getBoundingClientRect: () => ({ left: 118, width: 12 }),
                            }) as any,
                    },
                    container,
                } as any);

                internals(comp).updateCodeGenerationStatus(RepositoryType.TEMPLATE, (status: any) => ({
                    ...status,
                    state: 'running',
                    message: 'Generating...',
                }));

                await vi.advanceTimersByTimeAsync(0);

                expect(align).toHaveBeenCalledOnce();
                expect(container.style.insetInlineStart).toBe('20px');
                expect(setPropertySpy).toHaveBeenCalledWith('--p-popover-arrow-left', '102px');
            } finally {
                vi.useRealTimers();
            }
        });

        it('should clear subscription when restore check-only has no active job', () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            const clearSpy = vi.spyOn(internals(comp), 'clearJobSubscription');
            codeGenerationApi.generateCode.mockReturnValue(of({}));

            internals(comp).restoreCodeGenerationState();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { checkOnly: true });
            expect(clearSpy).toHaveBeenCalledWith(true);
        });

        it('should not cancel an in-flight status subscription while generation is still running', () => {
            const unsubscribe = vi.fn();
            comp.isGeneratingCode.set(true);
            internals(comp).statusSubscription = { unsubscribe };

            internals(comp).restoreCodeGenerationState();

            expect(unsubscribe).not.toHaveBeenCalled();
            expect(codeGenerationApi.generateCode).not.toHaveBeenCalled();
        });

        it('should restore the running repository from the check-only response instead of the selected tab', () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            const subscribeSpy = vi.spyOn(internals(comp), 'subscribeToJob').mockImplementation(() => {});
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-1', repositoryType: RepositoryType.TEMPLATE }));

            internals(comp).restoreCodeGenerationState();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { checkOnly: true });
            expect(internals(comp).activeCodeGenerationRepository).toBe(RepositoryType.TEMPLATE);
            expect(subscribeSpy).toHaveBeenCalledWith('job-1', RepositoryType.TEMPLATE);
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('running');
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.SOLUTION)?.state).not.toBe('running');
        });

        it('should persist code generation statuses in session storage', () => {
            internals(comp).initializeCodeGenerationRunStatuses([RepositoryType.SOLUTION]);
            internals(comp).updateCodeGenerationStatus(RepositoryType.SOLUTION, (status: any) => ({
                ...status,
                state: 'running',
                fileActivities: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        eventType: 'FILE_UPDATED',
                        path: 'src/main/java/Test.java',
                        iteration: 2,
                        timestamp: 123,
                    },
                ],
            }));

            expect(
                sessionStorageService.retrieve<{
                    updatedAt: number;
                    statuses: Array<{ repositoryType: RepositoryType; state: string; fileActivities: Array<{ path: string }> }>;
                }>('programming-exercise.code-generation.status.42'),
            ).toEqual(
                expect.objectContaining({
                    queuedRepositories: [],
                    initialAutoGeneration: false,
                    statuses: expect.arrayContaining([
                        expect.objectContaining({
                            repositoryType: RepositoryType.SOLUTION,
                            state: 'running',
                            fileActivities: [expect.objectContaining({ path: 'src/main/java/Test.java' })],
                        }),
                    ]),
                }),
            );
        });

        it('should restore persisted code generation statuses when the active job still exists', () => {
            comp.selectedRepository = RepositoryType.SOLUTION;
            const subscribeSpy = vi.spyOn(internals(comp), 'subscribeToJob').mockImplementation(() => {});
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now(),
                queuedRepositories: [RepositoryType.TESTS],
                activeRepository: RepositoryType.TEMPLATE,
                initialAutoGeneration: true,
                statuses: [
                    {
                        repositoryType: RepositoryType.TEMPLATE,
                        enabled: true,
                        state: 'running',
                        attempts: 1,
                        message: 'Still generating',
                        fileActivities: [
                            {
                                repositoryType: RepositoryType.TEMPLATE,
                                eventType: 'NEW_FILE',
                                path: 'src/test/java/NewTest.java',
                                iteration: 1,
                                timestamp: 123,
                            },
                        ],
                    },
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: false,
                        state: 'idle',
                        fileActivities: [],
                    },
                    {
                        repositoryType: RepositoryType.TESTS,
                        enabled: false,
                        state: 'idle',
                        fileActivities: [],
                    },
                ],
            });
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-1', repositoryType: RepositoryType.TEMPLATE }));

            internals(comp).restoreCodeGenerationState();

            expect(subscribeSpy).toHaveBeenCalledWith('job-1', RepositoryType.TEMPLATE);
            expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)).toEqual(
                expect.objectContaining({
                    state: 'running',
                    message: 'Still generating',
                    fileActivities: [expect.objectContaining({ path: 'src/test/java/NewTest.java' })],
                }),
            );
            expect(internals(comp).queuedCodeGenerationRepositories).toEqual([RepositoryType.TESTS]);
            expect(internals(comp).currentCodeGenerationUsesInitialIterationLimit).toBe(true);
        });

        it('should continue queued repositories after restoring an active generation job', async () => {
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now(),
                queuedRepositories: [RepositoryType.TEMPLATE, RepositoryType.TESTS],
                activeRepository: RepositoryType.SOLUTION,
                initialAutoGeneration: true,
                statuses: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: true,
                        state: 'running',
                        fileActivities: [],
                    },
                    {
                        repositoryType: RepositoryType.TEMPLATE,
                        enabled: true,
                        state: 'queued',
                        fileActivities: [],
                    },
                    {
                        repositoryType: RepositoryType.TESTS,
                        enabled: true,
                        state: 'queued',
                        fileActivities: [],
                    },
                ],
            });

            const solutionJob$ = new Subject<any>();
            const templateJob$ = new Subject<any>();
            codeGenerationApi.generateCode
                .mockReturnValueOnce(of({ jobId: 'job-solution', repositoryType: RepositoryType.SOLUTION }))
                .mockReturnValueOnce(of({}))
                .mockReturnValueOnce(of({ jobId: 'job-template' }));
            ws.subscribeToJob.mockReturnValueOnce(solutionJob$.asObservable()).mockReturnValueOnce(templateJob$.asObservable());

            internals(comp).restoreCodeGenerationState();
            solutionJob$.next({ type: 'DONE', success: true, completionStatus: 'SUCCESS', attempts: 1 });
            await Promise.resolve();

            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(2, 42, { checkOnly: true });
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(3, 42, {
                repositoryType: RepositoryType.TEMPLATE,
                checkOnly: false,
                initialAutoGeneration: true,
            });
            expect(internals(comp).activeCodeGenerationRepository).toBe(RepositoryType.TEMPLATE);
            expect(internals(comp).queuedCodeGenerationRepositories).toEqual([RepositoryType.TESTS]);
        });

        it('should resume the persisted queue when refresh happens during slot release polling', () => {
            const subscribeSpy = vi.spyOn(internals(comp), 'subscribeToJob').mockImplementation(() => {});
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now(),
                queuedRepositories: [RepositoryType.TEMPLATE, RepositoryType.TESTS],
                initialAutoGeneration: true,
                statuses: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: true,
                        state: 'success',
                        attempts: 1,
                        fileActivities: [],
                    },
                    {
                        repositoryType: RepositoryType.TEMPLATE,
                        enabled: true,
                        state: 'queued',
                        fileActivities: [],
                    },
                    {
                        repositoryType: RepositoryType.TESTS,
                        enabled: true,
                        state: 'queued',
                        fileActivities: [],
                    },
                ],
            });
            codeGenerationApi.generateCode.mockReturnValueOnce(of({})).mockReturnValueOnce(of({ jobId: 'job-template' }));

            internals(comp).restoreCodeGenerationState();

            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(1, 42, { checkOnly: true });
            expect(codeGenerationApi.generateCode).toHaveBeenNthCalledWith(2, 42, {
                repositoryType: RepositoryType.TEMPLATE,
                checkOnly: false,
                initialAutoGeneration: true,
            });
            expect(subscribeSpy).toHaveBeenCalledWith('job-template', RepositoryType.TEMPLATE);
            expect(internals(comp).activeCodeGenerationRepository).toBe(RepositoryType.TEMPLATE);
            expect(internals(comp).queuedCodeGenerationRepositories).toEqual([RepositoryType.TESTS]);
        });

        it('should clear the restore subscription when the check-only response contains an unsupported repository type', () => {
            const clearSpy = vi.spyOn(internals(comp), 'clearJobSubscription');
            const subscribeSpy = vi.spyOn(internals(comp), 'subscribeToJob').mockImplementation(() => {});
            codeGenerationApi.generateCode.mockReturnValue(of({ jobId: 'job-1', repositoryType: RepositoryType.ASSIGNMENT }));

            internals(comp).restoreCodeGenerationState();

            expect(codeGenerationApi.generateCode).toHaveBeenCalledWith(42, { checkOnly: true });
            expect(clearSpy).toHaveBeenCalledWith(true);
            expect(subscribeSpy).not.toHaveBeenCalled();
        });

        it('should clear persisted code generation statuses when no active job exists anymore', () => {
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now(),
                statuses: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: true,
                        state: 'running',
                        fileActivities: [],
                    },
                ],
            });
            codeGenerationApi.generateCode.mockReturnValue(of({}));

            internals(comp).restoreCodeGenerationState();

            expect(sessionStorageService.retrieve('programming-exercise.code-generation.status.42')).toBeUndefined();
        });

        it('should discard expired persisted code generation statuses', () => {
            const removeSpy = vi.spyOn(sessionStorageService, 'remove');
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now() - 3_600_001,
                statuses: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: true,
                        state: 'running',
                        fileActivities: [],
                    },
                ],
            });

            expect(internals(comp).loadPersistedCodeGenerationState()).toBeUndefined();
            expect(removeSpy).toHaveBeenCalledWith('programming-exercise.code-generation.status.42');
        });

        it('should discard malformed persisted code generation statuses', () => {
            const removeSpy = vi.spyOn(sessionStorageService, 'remove');
            vi.spyOn(sessionStorageService, 'retrieve').mockImplementation(() => {
                throw new Error('malformed session data');
            });

            expect(internals(comp).loadPersistedCodeGenerationState()).toBeUndefined();
            expect(removeSpy).toHaveBeenCalledWith('programming-exercise.code-generation.status.42');
        });

        it('should clear persisted code generation statuses when restoring the active job fails', () => {
            const clearSpy = vi.spyOn(internals(comp), 'clearJobSubscription');
            sessionStorageService.store('programming-exercise.code-generation.status.42', {
                updatedAt: Date.now(),
                statuses: [
                    {
                        repositoryType: RepositoryType.SOLUTION,
                        enabled: true,
                        state: 'running',
                        fileActivities: [],
                    },
                ],
            });
            codeGenerationApi.generateCode.mockReturnValue(throwError(() => new Error('restore failed')));

            internals(comp).restoreCodeGenerationState();

            expect(sessionStorageService.retrieve('programming-exercise.code-generation.status.42')).toBeUndefined();
            expect(clearSpy).toHaveBeenCalledWith(true);
        });
    });

    describe('Review Comments', () => {
        it('loadExercise sets review context and reloads threads when returned exercise has an id', () => {
            const superLoadSpy = vi.spyOn(CodeEditorInstructorBaseContainerComponent.prototype, 'loadExercise').mockReturnValue(of({ id: 55 } as any));

            comp.loadExercise(55).subscribe();

            expect(superLoadSpy).toHaveBeenCalledWith(55);
            expect(reviewCommentService.setExercise).toHaveBeenCalledWith(55);
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();

            superLoadSpy.mockRestore();
        });

        it('loadExercise does not set review context when returned exercise has no id', () => {
            const superLoadSpy = vi.spyOn(CodeEditorInstructorBaseContainerComponent.prototype, 'loadExercise').mockReturnValue(of({} as any));

            comp.loadExercise(55).subscribe();

            expect(superLoadSpy).toHaveBeenCalledWith(55);
            expect(reviewCommentService.setExercise).not.toHaveBeenCalled();
            expect(reviewCommentService.reloadThreads).not.toHaveBeenCalled();

            superLoadSpy.mockRestore();
        });

        it('onCommit clears draft widgets and reloads threads', () => {
            const clearEditorDraftsSpy = vi.spyOn(getCodeEditorContainer(comp).monacoEditor(), 'clearReviewCommentDrafts');

            comp.onCommit();

            expect(clearEditorDraftsSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        });

        it('onProblemStatementSaved clears markdown drafts and reloads threads', () => {
            const mockInstructions = internals(comp).editableInstructions();
            const clearInstructionDraftsSpy = vi.spyOn(mockInstructions, 'clearReviewCommentDrafts');

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
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);

            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);
            expect(artemisIntelligenceService.consistencyCheck).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        });

        it('shows success when no new consistency threads are persisted after consistency check', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [mockIssues[0]] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');
            const warningSpy = vi.spyOn(alertService, 'warning');

            comp.checkConsistencies(comp.exercise!);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledOnce();
            expect(warningSpy).not.toHaveBeenCalled();
            expect(comp.showConsistencyIssuesToolbar()).toBe(false);
        });

        it('shows warning and toolbar when new consistency threads are persisted after consistency check', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');
            const warningSpy = vi.spyOn(alertService, 'warning');
            reviewCommentService.reloadThreads.mockImplementationOnce((onLoaded?: () => void) => {
                reviewCommentService.threads.set(createConsistencyThreads([mockIssues[0]]) as any);
                onLoaded?.();
            });

            comp.checkConsistencies(comp.exercise!);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(warningSpy).toHaveBeenCalledOnce();
            expect(successSpy).not.toHaveBeenCalled();
            expect(comp.showConsistencyIssuesToolbar()).toBe(true);
        });

        it('shows success when no new issues are reported, even if persisted consistency threads already exist', () => {
            reviewCommentService.threads.set(createConsistencyThreads([mockIssues[0]]) as any);
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');
            const warningSpy = vi.spyOn(alertService, 'warning');

            comp.checkConsistencies(comp.exercise!);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).toHaveBeenCalledOnce();
            expect(successSpy).toHaveBeenCalledOnce();
            expect(warningSpy).not.toHaveBeenCalled();
            expect(comp.showConsistencyIssuesToolbar()).toBe(false);
        });

        it('error when first consistency check fails', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const failSpy = vi.spyOn(alertService, 'error');

            comp.checkConsistencies(comp.exercise!);
            expect(consistencyCheckService.checkConsistencyForProgrammingExercise).toHaveBeenCalledWith(42);

            expect(check1Spy).toHaveBeenCalledOnce();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
            expect(reviewCommentService.reloadThreads).not.toHaveBeenCalled();
        });

        it('error when exercise id undefined', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([error1]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const failSpy = vi.spyOn(alertService, 'error');

            comp.checkConsistencies({ id: undefined } as any);

            expect(check1Spy).not.toHaveBeenCalled();
            expect(check2Spy).not.toHaveBeenCalled();
            expect(failSpy).toHaveBeenCalledOnce();
        });

        it('check isLoading propagates correctly', () => {
            (artemisIntelligenceService as any).isLoading = () => true;
            expect(comp.isCheckingConsistency()).toBe(true);

            (artemisIntelligenceService as any).isLoading = () => false;
            expect(comp.isCheckingConsistency()).toBe(false);
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
            expect(comp.showConsistencyIssuesToolbar()).toBe(false);

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.showConsistencyIssuesToolbar()).toBe(true);

            const sorted = comp.sortedIssues();
            expect(comp.selectedIssue).toEqual(sorted[0]);
        });

        it('should exclude resolved consistency threads from the navigation list', () => {
            const threads = createConsistencyThreads(mockIssues);
            threads[0].resolved = true;
            threads[3].resolved = true;
            reviewCommentService.threads.set(threads as any);

            const sorted = comp.sortedIssues();
            expect(sorted).toHaveLength(mockIssues.length - 2);
            expect(sorted.some((issue) => issue.threadId === threads[0].id)).toBe(false);
            expect(sorted.some((issue) => issue.threadId === threads[3].id)).toBe(false);

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.selectedIssue).toEqual(sorted[0]);
        });

        it('should navigate global next', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue = sorted[0];

            const jumpSpy = vi.spyOn(internals(comp), 'jumpToLocation').mockImplementation(() => {});

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

            const jumpSpy = vi.spyOn(internals(comp), 'jumpToLocation').mockImplementation(() => {});

            const lastIssue = sorted[sorted.length - 1];

            comp.navigateGlobal(-1);

            expect(comp.selectedIssue).toBe(lastIssue);
            expect(jumpSpy).toHaveBeenCalledWith(lastIssue);
        });

        it('navigates to PROBLEM_STATEMENT and calls jumpToLine', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const issue = comp.sortedIssues().find((sortedIssue) => sortedIssue.targetType === CommentThreadLocationType.PROBLEM_STATEMENT)!;

            const mockEditable = { jumpToLine: vi.fn() };
            setEditableInstructions(comp, mockEditable);
            const jumpSpy = mockEditable.jumpToLine;

            internals(comp).jumpToLocation(issue);

            expect(getCodeEditorContainer(comp).selectedFile).toBe('problem_statement.md');
            expect(jumpSpy).toHaveBeenCalledWith(issue.lineNumber);
        });

        it('onEditorLoaded jumps immediately when file is already selected without triggering onFileLoad', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            const targetLine = 42;
            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;
            getCodeEditorContainer(comp).selectedFile = targetFile;

            const onFileLoadSpy = vi.spyOn(comp, 'onFileLoad');
            const onFileSyncLoadSpy = vi.spyOn(internals(comp), 'onFileSyncLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).not.toHaveBeenCalled();
            expect(onFileSyncLoadSpy).not.toHaveBeenCalled();
            expect(getCodeEditorContainer(comp).jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(getCodeEditorContainer(comp).selectedFile).toBe(targetFile);
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onEditorLoaded sets selectedFile when file is not selected yet', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            comp.fileToJumpOn = targetFile;
            getCodeEditorContainer(comp).selectedFile = 'some/other/file.java';

            const onFileLoadSpy = vi.spyOn(comp, 'onFileLoad');

            comp.onEditorLoaded();

            expect(onFileLoadSpy).not.toHaveBeenCalled();
            expect(getCodeEditorContainer(comp).selectedFile).toBe(targetFile);
        });

        it('onEditorLoaded keeps deferred jump state until onFileLoad is called', () => {
            const targetFile = 'src/tests/ExampleTest.java';
            const targetLine = 42;
            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;
            getCodeEditorContainer(comp).selectedFile = 'some/other/file.java';

            comp.onEditorLoaded();

            expect(getCodeEditorContainer(comp).selectedFile).toBe(targetFile);
            expect(comp.fileToJumpOn).toBe(targetFile);
            expect(comp.lineJumpOnFileLoad).toBe(targetLine);

            comp.onFileLoad(targetFile);

            expect(getCodeEditorContainer(comp).jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onFileLoad jumps to line and clears lineJumpOnFileLoad when file matches', () => {
            const targetFile = 'src/solution/Solution.java';
            const targetLine = 60;

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = targetLine;

            comp.onFileLoad(targetFile);

            expect(getCodeEditorContainer(comp).jumpToLine).toHaveBeenCalledWith(targetLine);
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
        });

        it('onFileLoad does nothing if file does not match fileToJumpOn', () => {
            comp.fileToJumpOn = 'src/solution/Solution.java';
            comp.lineJumpOnFileLoad = 60;

            comp.onFileLoad('src/tests/ExampleTest.java');

            expect(getCodeEditorContainer(comp).jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBe(60);
        });

        it('onFileLoad does nothing if lineJumpOnFileLoad is undefined', () => {
            const targetFile = 'src/solution/Solution.java';

            comp.fileToJumpOn = targetFile;
            comp.lineJumpOnFileLoad = undefined;

            comp.onFileLoad(targetFile);

            expect(getCodeEditorContainer(comp).jumpToLine).not.toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
        });

        it('shows error and clears jump state when repository selection fails', () => {
            const issue = {
                targetType: CommentThreadLocationType.TEST_REPO,
                filePath: 'src/tests/ExampleTest.java',
                lineNumber: 70,
            };
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue('SOLUTION');

            const error = new Error('repo selection failed');
            vi.spyOn(comp, 'selectTestRepository').mockImplementation(() => {
                throw error;
            });

            const alertErrorSpy = vi.spyOn(alertService, 'error');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).jumpToLocation(issue);

            expect(alertErrorSpy).toHaveBeenCalled();
            expect(comp.lineJumpOnFileLoad).toBeUndefined();
            expect(comp.fileToJumpOn).toBeUndefined();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects template repo when target is TEMPLATE_REPO and current repo differs', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.SOLUTION);
            const selectTemplateSpy = vi.spyOn(comp, 'selectTemplateParticipation');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({ targetType: CommentThreadLocationType.TEMPLATE_REPO, filePath: 'src/template/A.java', lineNumber: 10 });

            expect(selectTemplateSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects solution repo when target is SOLUTION_REPO and current repo differs', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.TEMPLATE);
            const selectSolutionSpy = vi.spyOn(comp, 'selectSolutionParticipation');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({ targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/solution/B.java', lineNumber: 11 });

            expect(selectSolutionSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects test repo when target is TEST_REPO and current repo differs', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.SOLUTION);
            const selectTestSpy = vi.spyOn(comp, 'selectTestRepository');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({ targetType: CommentThreadLocationType.TEST_REPO, filePath: 'src/test/C.java', lineNumber: 12 });

            expect(selectTestSpy).toHaveBeenCalledOnce();
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when target is AUXILIARY_REPO and current repo differs', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.TEMPLATE);
            comp.selectAuxiliaryRepository = vi.fn();
            const selectAuxSpy = vi.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 77,
                filePath: 'src/aux/D.java',
                lineNumber: 13,
            });

            expect(selectAuxSpy).toHaveBeenCalledWith(77);
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when already in AUXILIARY but repository id differs', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.AUXILIARY);
            comp.selectedRepositoryId = 12;
            comp.selectAuxiliaryRepository = vi.fn();
            const selectAuxSpy = vi.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({
                targetType: CommentThreadLocationType.AUXILIARY_REPO,
                auxiliaryRepositoryId: 77,
                filePath: 'src/aux/D.java',
                lineNumber: 13,
            });

            expect(selectAuxSpy).toHaveBeenCalledWith(77);
            expect(onEditorLoadedSpy).not.toHaveBeenCalled();
        });

        it('navigateToLocation selects auxiliary repo when auxiliaryRepositoryId is 0', () => {
            getCodeEditorContainer(comp).selectedRepository = vi.fn().mockReturnValue(RepositoryType.TEMPLATE);
            comp.selectAuxiliaryRepository = vi.fn();
            const selectAuxSpy = vi.spyOn(comp, 'selectAuxiliaryRepository');
            const onEditorLoadedSpy = vi.spyOn(comp, 'onEditorLoaded');

            internals(comp).navigateToLocation({
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
            internals(comp).showConsistencyIssuesToolbar.set(true);
            comp.selectedIssue = comp.sortedIssues()[0];

            vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise!);

            expect(comp.showConsistencyIssuesToolbar()).toBe(false);
            expect(comp.selectedIssue).toBeUndefined();
        });
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Diff Editor', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    beforeEach(async () => {
        await configureTestBed();

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({ problemStatement: 'Original' });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    it('should accept refinement and update problem statement', () => {
        // Simulate refinement setting up diff mode
        comp.showDiff.set(true);

        comp.closeDiff();

        expect(comp.showDiff()).toBe(false);
    });

    it('should revert refinement', () => {
        comp.showDiff.set(true);
        // Mock the internal editableInstructions to have revertAll and getCurrentContent methods
        const mockEditable = {
            revertAll: vi.fn(),
            getCurrentContent: vi.fn().mockReturnValue('Reverted content'),
        };
        setEditableInstructions(comp, mockEditable);

        comp.revertAllRefinement();

        expect(mockEditable.revertAll).toHaveBeenCalled();
        expect(comp.showDiff()).toBe(false);
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Problem Statement Refinement', () => {
    setupTestBed({ zoneless: true });

    // Validation, error handling, and edge cases are covered by problem-statement.service.spec.ts.
    // These tests only verify the component wires up to ProblemStatementService correctly.

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let problemStatementService: {
        refineTargeted: ReturnType<typeof vi.fn>;
        refineGlobally: ReturnType<typeof vi.fn>;
        generateProblemStatement: ReturnType<typeof vi.fn>;
        loadTemplate: ReturnType<typeof vi.fn>;
    };

    beforeEach(async () => {
        await configureTestBed([
            {
                provide: ProblemStatementService,
                useValue: { refineTargeted: vi.fn(), refineGlobally: vi.fn(), generateProblemStatement: vi.fn(), loadTemplate: vi.fn() },
            },
        ]);

        problemStatementService = TestBed.inject(ProblemStatementService) as unknown as {
            refineTargeted: ReturnType<typeof vi.fn>;
            refineGlobally: ReturnType<typeof vi.fn>;
            generateProblemStatement: ReturnType<typeof vi.fn>;
            loadTemplate: ReturnType<typeof vi.fn>;
        };

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({ problemStatement: 'Original problem statement' });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    it('should delegate inline refinement to service and show diff on success', () => {
        problemStatementService.refineTargeted.mockReturnValue(of({ success: true, content: 'Refined content' }));

        comp.onInlineRefinement({ instruction: 'Improve this', startLine: 1, endLine: 2, startColumn: 1, endColumn: 10 });

        expect(problemStatementService.refineTargeted).toHaveBeenCalledWith(
            comp.exercise,
            'Original problem statement',
            expect.objectContaining({ instruction: 'Improve this' }),
            expect.any(Function),
        );
        expect(comp.showDiff()).toBe(true);
    });

    it('should handle toggleRefinementPopover gracefully when popover is undefined', () => {
        // popover viewChild is undefined because the template is overridden to empty
        expect(() => comp.toggleRefinementPopover(new Event('click'))).not.toThrow();
    });

    it('should preserve refinement prompt when popover hides (prompt is never cleared on dismiss)', () => {
        comp.refinementPrompt.set('Some prompt');
        // The prompt signal should persist since there's no onHide handler clearing it
        expect(comp.refinementPrompt()).toBe('Some prompt');
    });

    it('should delegate global refinement to service and show diff on success', () => {
        problemStatementService.refineGlobally.mockReturnValue(of({ success: true, content: 'Refined content' }));

        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.templateProblemStatement.set('Template');
        comp.aiOps.currentProblemStatement.set('Original problem statement');
        comp.refinementPrompt.set('Improve clarity');

        comp.submitRefinement();

        expect(problemStatementService.refineGlobally).toHaveBeenCalledWith(comp.exercise, 'Original problem statement', 'Improve clarity', expect.any(Function));
        expect(comp.showDiff()).toBe(true);
    });

    it('should not submit when prompt is empty', () => {
        comp.refinementPrompt.set('   ');
        comp.submitRefinement();
        expect(problemStatementService.refineGlobally).not.toHaveBeenCalled();
        expect(problemStatementService.generateProblemStatement).not.toHaveBeenCalled();
    });
});

describe('CodeEditorInstructorBaseContainerComponent - file sync binding', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    /** Minimal monaco model/editor doubles sufficient for binding tests. */
    function makeMonacoDoubles() {
        const model = { setValue: vi.fn(), setEOL: vi.fn(), onDidChangeContent: vi.fn(() => ({ dispose: vi.fn() })) } as any;
        const editorInstance = { getModel: vi.fn(() => model), getEditor: vi.fn(), getText: vi.fn(() => 'content') } as any;
        return { model, editorInstance };
    }

    beforeEach(async () => {
        await configureTestBed();
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise();
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    /** Builds the fileSyncService stub used by all three tests. */
    function makeFileSyncStub(stateReplaced$: Subject<{ filePath: string } & FileSyncState>, openFileResult: any = {}) {
        return {
            isInitialized: vi.fn(() => true),
            openFile: vi.fn(() => openFileResult),
            closeFile: vi.fn(),
            reset: vi.fn(),
            stateReplaced$: stateReplaced$.asObservable(),
        };
    }

    /** Builds the codeEditorContainer stub used by all three tests. monacoEditor is a viewChild() signal. */
    function makeContainerStub(model: any, fileText = '') {
        const monacoEditor = {
            binaryFileSelected: vi.fn(() => false),
            editor: vi.fn(() => ({
                getModel: vi.fn(() => model),
                getEditor: vi.fn(() => ({})),
                getText: vi.fn(() => fileText),
            })),
        };
        return {
            monacoEditor: () => monacoEditor,
        };
    }

    it('normalizes CRLF fallback content and enforces LF EOL before binding', () => {
        const stateReplaced$ = new Subject<{ filePath: string } & FileSyncState>();
        const { model } = makeMonacoDoubles();
        const openFile = vi.fn(() => ({ doc: {}, text: { toString: () => '' }, awareness: {} }));

        internals(comp).fileSyncService = {
            isInitialized: vi.fn(() => true),
            openFile,
            closeFile: vi.fn(),
            reset: vi.fn(),
            stateReplaced$: stateReplaced$.asObservable(),
        };
        const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding').mockImplementation(() => undefined);

        setCodeEditorContainer(comp, makeContainerStub(model, 'line1\r\nline2\r\n'));

        internals(comp).onFileSyncLoad('src/Main.java');

        expect(openFile).toHaveBeenCalledWith('src/Main.java', 'line1\nline2\n');
        expect(model.setEOL).toHaveBeenCalledOnce();
        expect(model.setValue).toHaveBeenCalledWith('');
        expect(createFileBindingSpy).toHaveBeenCalledOnce();
    });

    it('stateReplaced$ for the active file tears down the old binding, sets model value, and rebinds', () => {
        const stateReplaced$ = new Subject<{ filePath: string } & FileSyncState>();
        const { model } = makeMonacoDoubles();

        const oldBinding = { destroy: vi.fn() };
        const newBinding = { destroy: vi.fn() };
        let bindingCallCount = 0;

        internals(comp).fileSyncService = makeFileSyncStub(stateReplaced$, { doc: {}, text: { toString: () => '' }, awareness: {} });

        const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding').mockImplementation(() => {
            internals(comp).currentFileBinding = [oldBinding, newBinding][bindingCallCount++];
        });

        setCodeEditorContainer(comp, makeContainerStub(model));

        // Load the file — creates the first binding and subscribes to stateReplaced$
        internals(comp).onFileSyncLoad('src/Main.java');
        expect(createFileBindingSpy).toHaveBeenCalledOnce();

        // Emit a state replacement for the same file
        const newText = { toString: () => 'replacement text' } as any;
        stateReplaced$.next({ filePath: 'src/Main.java', doc: {} as any, text: newText, awareness: {} as any });

        // Old binding must be destroyed before model mutation
        expect(oldBinding.destroy).toHaveBeenCalled();
        // Model must be seeded with new content
        expect(model.setValue).toHaveBeenCalledWith('replacement text');
        // A new binding must be created
        expect(createFileBindingSpy).toHaveBeenCalledTimes(2);
    });

    it('stateReplaced$ for a different file does not affect the active binding', () => {
        const stateReplaced$ = new Subject<{ filePath: string } & FileSyncState>();
        const { model } = makeMonacoDoubles();
        const binding = { destroy: vi.fn() };

        internals(comp).fileSyncService = makeFileSyncStub(stateReplaced$, { doc: {}, text: { toString: () => '' }, awareness: {} });

        const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding').mockImplementation(() => {
            internals(comp).currentFileBinding = binding;
        });

        setCodeEditorContainer(comp, makeContainerStub(model));

        internals(comp).onFileSyncLoad('src/Main.java');

        // Emit for a DIFFERENT file — must be ignored
        stateReplaced$.next({ filePath: 'src/Other.java', doc: {} as any, text: { toString: () => 'other' } as any, awareness: {} as any });

        expect(binding.destroy).not.toHaveBeenCalled();
        expect(model.setValue).not.toHaveBeenCalledWith('other');
        // createFileBinding still only called once (initial load)
        expect(createFileBindingSpy).toHaveBeenCalledOnce();
    });

    it('double-destroy guard in the real createFileBinding prevents the underlying destroy from being invoked twice', async () => {
        // Retrieve the mock destroy spy injected by the module-level vi.mock('y-monaco').
        const yMonaco = await import('y-monaco');
        const innerDestroy: ReturnType<typeof vi.fn> = (yMonaco.MonacoBinding as any).__mockDestroy;
        innerDestroy.mockClear();

        const fakeSyncState = { doc: {} as any, text: {} as any, awareness: {} as any };
        const fakeModel = {} as any;
        const fakeEditor = {} as any;

        // Call the REAL createFileBinding — not a mock — so we exercise the actual guard.
        internals(comp).createFileBinding(fakeSyncState, fakeModel, fakeEditor);
        const firstBinding = internals(comp).currentFileBinding;

        // Call destroy twice; the second call must be a no-op (guard in production code).
        firstBinding.destroy();
        firstBinding.destroy();

        expect(innerDestroy).toHaveBeenCalledOnce();

        // teardownFileBinding must also be idempotent when called more than once.
        internals(comp).teardownFileBinding();
        internals(comp).teardownFileBinding();
        // No error thrown — guard works
    });

    describe('onFileSyncLoad early-return guards', () => {
        it('does nothing when fileSyncService is not initialized', () => {
            const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding');
            internals(comp).fileSyncService = { isInitialized: vi.fn(() => false), reset: vi.fn(), stateReplaced$: new Subject().asObservable() };

            internals(comp).onFileSyncLoad('src/Main.java');

            expect(createFileBindingSpy).not.toHaveBeenCalled();
        });

        it('does nothing when monacoEditor is not available', () => {
            const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding');
            internals(comp).fileSyncService = { isInitialized: vi.fn(() => true), reset: vi.fn(), stateReplaced$: new Subject().asObservable() };
            setCodeEditorContainer(comp, { monacoEditor: undefined });

            internals(comp).onFileSyncLoad('src/Main.java');

            expect(createFileBindingSpy).not.toHaveBeenCalled();
        });

        it('does nothing when a binary file is selected', () => {
            const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding');
            internals(comp).fileSyncService = { isInitialized: vi.fn(() => true), reset: vi.fn(), stateReplaced$: new Subject().asObservable() };
            const monacoEditor = { binaryFileSelected: vi.fn(() => true) };
            setCodeEditorContainer(comp, { monacoEditor: () => monacoEditor });

            internals(comp).onFileSyncLoad('src/Image.png');

            expect(createFileBindingSpy).not.toHaveBeenCalled();
        });

        it('does nothing when the model is not available', () => {
            const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding');
            internals(comp).fileSyncService = { isInitialized: vi.fn(() => true), openFile: vi.fn(), reset: vi.fn(), stateReplaced$: new Subject().asObservable() };
            const monacoEditor = {
                binaryFileSelected: vi.fn(() => false),
                editor: vi.fn(() => ({ getModel: vi.fn(() => undefined), getEditor: vi.fn(() => ({})), getText: vi.fn(() => '') })),
            };
            setCodeEditorContainer(comp, { monacoEditor: () => monacoEditor });

            internals(comp).onFileSyncLoad('src/Main.java');

            expect(createFileBindingSpy).not.toHaveBeenCalled();
        });

        it('does nothing when openFile returns undefined', () => {
            const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding');
            const model = { setValue: vi.fn(), setEOL: vi.fn() };
            internals(comp).fileSyncService = {
                isInitialized: vi.fn(() => true),
                openFile: vi.fn(() => undefined),
                closeFile: vi.fn(),
                reset: vi.fn(),
                stateReplaced$: new Subject().asObservable(),
            };
            const monacoEditor = {
                binaryFileSelected: vi.fn(() => false),
                editor: vi.fn(() => ({ getModel: vi.fn(() => model), getEditor: vi.fn(() => ({})), getText: vi.fn(() => '') })),
            };
            setCodeEditorContainer(comp, { monacoEditor: () => monacoEditor });

            internals(comp).onFileSyncLoad('src/Main.java');

            expect(createFileBindingSpy).not.toHaveBeenCalled();
        });
    });
});
