import { HttpErrorResponse } from '@angular/common/http';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('y-monaco', () => {
    const mockDestroy = vi.fn();
    const MockMonacoBinding = vi.fn(function (this: any) {
        this.destroy = mockDestroy;
    });
    (MockMonacoBinding as any).__mockDestroy = mockDestroy;
    return { MonacoBinding: MockMonacoBinding };
});
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Provider, Signal, WritableSignal, signal } from '@angular/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { FileSyncState } from 'app/exercise/synchronization/services/code-editor-file-sync.service';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { DomainChange, DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_HYPERION, PROFILE_LOCALCI } from 'app/app.constants';
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
import { ConsistencyCheckResponse } from 'app/openapi/model/consistency-check-response';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { ConsistencyCheckError, ErrorType } from 'app/programming/shared/entities/consistency-check-result.model';

import { ConsistencyIssue } from 'app/openapi/model/consistency-issue';
import { faCircleExclamation, faCircleInfo, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseReviewCommentService, ReviewAdaptationAvailability, ReviewAdaptationRequest } from 'app/exercise/review/exercise-review-comment.service';
import { ExerciseEditorSyncService } from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { HyperionGenerationActivityFacade } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { TumUiConfirmationService } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';

type ComponentInternalsOverrides = {
    codeEditorContainer: Signal<any>;
    editableInstructions: Signal<any>;
    showConsistencyIssuesToolbar: WritableSignal<boolean>;
    fileSyncService: any;
    currentFileBinding: any;
    applyDomainChange: (domainType: any, domainValue: any) => void;
    jumpToLocation: (issue: any) => void;
    navigateToLocation: (location: any) => void;
    onFileSyncLoad: (fileName: string) => void;
    createFileBinding: (syncState: any, model: any, editorInstance: any) => void;
    teardownFileBinding: () => void;
};
type ComponentInternals = Omit<CodeEditorInstructorAndEditorContainerComponent, keyof ComponentInternalsOverrides> & ComponentInternalsOverrides;
const internals = (c: CodeEditorInstructorAndEditorContainerComponent): ComponentInternals => c as unknown as ComponentInternals;

interface CodeEditorContainerStub {
    actions?: () => { onSave: ReturnType<typeof vi.fn> };
    allowNextUnloadWithoutConfirmation?: ReturnType<typeof vi.fn>;
    canDeactivate?: () => boolean;
    hasCleanRepositoryState?: () => boolean;
    hasReviewCommentDrafts?: () => boolean;
    selectedFile?: string;
    selectedRepository?: ReturnType<typeof vi.fn>;
    problemStatementIdentifier?: string;
    jumpToLine?: ReturnType<typeof vi.fn>;
    initializeProperties?: ReturnType<typeof vi.fn>;
    monacoEditor?: () => any;
    openEditorBottomPanel?: ReturnType<typeof vi.fn>;
}

function setCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent, stub: CodeEditorContainerStub | undefined): void {
    const completeStub = stub
        ? {
              allowNextUnloadWithoutConfirmation: vi.fn(),
              openEditorBottomPanel: vi.fn(),
              canDeactivate: () => true,
              hasCleanRepositoryState: () => true,
              hasReviewCommentDrafts: () => false,
              ...stub,
          }
        : undefined;
    internals(comp).codeEditorContainer = (() => completeStub) as unknown as Signal<any>;
}

function getCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent): any {
    return internals(comp).codeEditorContainer();
}

function setEditableInstructions(comp: CodeEditorInstructorAndEditorContainerComponent, stub: any): void {
    internals(comp).editableInstructions = (() => stub) as unknown as Signal<any>;
}

function createDefaultContainerStub(): CodeEditorContainerStub {
    const actions = { onSave: vi.fn() };
    const monacoEditor = { clearReviewCommentDrafts: vi.fn() };
    return {
        actions: () => actions,
        canDeactivate: () => true,
        hasCleanRepositoryState: () => true,
        hasReviewCommentDrafts: () => false,
        selectedFile: undefined as string | undefined,
        selectedRepository: vi.fn().mockReturnValue('SOLUTION'),
        problemStatementIdentifier: 'problem_statement.md',
        jumpToLine: vi.fn(),
        initializeProperties: vi.fn(),
        monacoEditor: () => monacoEditor,
        openEditorBottomPanel: vi.fn(),
    };
}

function createMockExercise(overrides: Partial<ProgrammingExercise> = {}): ProgrammingExercise {
    const mockCourse = new Course();
    mockCourse.id = 1;

    const exercise = new ProgrammingExercise(mockCourse, undefined);
    exercise.id = 42;
    exercise.problemStatement = 'Test problem statement';
    exercise.projectType = ProjectType.GRADLE_GRADLE;
    exercise.templateParticipation!.id = 420;
    exercise.solutionParticipation!.id = 421;

    return Object.assign(exercise, overrides);
}

function getBaseProviders(additionalProviders: Provider[] = []): Provider[] {
    return [
        { provide: AlertService, useClass: MockAlertService },
        { provide: ProfileService, useClass: MockProfileService },
        { provide: Router, useClass: MockRouter },
        { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
        { provide: ProgrammingExerciseParticipationService, useValue: { retrieveCommitHistoryForTemplateSolutionOrTests: vi.fn() } },
        { provide: CourseExerciseService, useClass: MockCourseExerciseService },
        { provide: DomainService, useValue: { setDomain: vi.fn() } },
        { provide: Location, useValue: { replaceState: vi.fn() } },
        { provide: ParticipationService, useClass: MockParticipationService },
        { provide: ActivatedRoute, useValue: { params: of({}) } },
        { provide: NgbModal, useValue: { open: vi.fn(() => ({ componentInstance: {}, result: Promise.resolve() })) } },
        { provide: TumUiConfirmationService, useValue: { confirm: vi.fn(), close: vi.fn() } },
        { provide: CodeEditorRepositoryService, useValue: { pull: vi.fn(() => of(void 0)) } },
        { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: vi.fn(() => of({})) } },
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: vi.fn() } },
        { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: vi.fn(), isLoading: () => false } },
        { provide: ExerciseEditorSyncService, useValue: { connect: vi.fn(), disconnect: vi.fn(), subscribeToUpdates: vi.fn(() => of()) } },
        {
            provide: HyperionGenerationActivityFacade,
            useValue: {
                connect: vi.fn(),
                generationCompleted: new Subject(),
                generationReverted: new Subject(),
                running: signal(false),
                statusLoading: signal(false),
                statusLoadFailed: signal(false),
                attachToJob: vi.fn(),
            },
        },
        { provide: HyperionJobRegistryService, useValue: { track: vi.fn() } },
        ...additionalProviders,
    ];
}

async function configureTestBed(additionalProviders: Provider[] = []): Promise<void> {
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
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    let alertService: AlertService;
    let profileService: ProfileService;
    let artemisIntelligenceService: ArtemisIntelligenceService;
    let consistencyCheckService: ConsistencyCheckService;
    let reviewCommentService: {
        setExercise: ReturnType<typeof vi.fn>;
        reloadThreads: ReturnType<typeof vi.fn>;
        getSelectedFeedbackThreadIdsForRepository: ReturnType<typeof vi.fn>;
        threads: WritableSignal<any[]>;
        connectAdaptation: ReturnType<typeof vi.fn>;
        adaptationRequests: Observable<ReviewAdaptationRequest>;
    };

    const mockIssues: ConsistencyIssue[] = [
        {
            severity: 'HIGH',
            category: 'CONSTRUCTOR_PARAMETER_MISMATCH',
            description: 'Problem statement inconsistency',
            suggestedFix: 'Review the problem statement file.',
            relatedLocations: [
                {
                    type: 'PROBLEM_STATEMENT',
                    filePath: 'problem_statement.md',
                    startLine: 1,
                    endLine: 42,
                },
            ],
        },
        {
            severity: 'MEDIUM',
            category: 'METHOD_PARAMETER_MISMATCH',
            description: 'Template repository issue',
            suggestedFix: 'Fix template repository references.',
            relatedLocations: [
                {
                    type: 'TEMPLATE_REPOSITORY',
                    filePath: 'src/template/Example.java',
                    startLine: 5,
                    endLine: 50,
                },
            ],
        },
        {
            severity: 'MEDIUM',
            category: 'ATTRIBUTE_TYPE_MISMATCH',
            description: 'Solution repository issue',
            suggestedFix: 'Fix solution repository references.',
            relatedLocations: [
                {
                    type: 'SOLUTION_REPOSITORY',
                    filePath: 'src/solution/Solution.java',
                    startLine: 3,
                    endLine: 60,
                },
            ],
        },
        {
            severity: 'LOW',
            category: 'IDENTIFIER_NAMING_INCONSISTENCY',
            description: 'Tests repository issue',
            suggestedFix: 'Adjust tests in test repository.',
            relatedLocations: [
                {
                    type: 'TESTS_REPOSITORY',
                    filePath: 'src/tests/ExampleTest.java',
                    startLine: 10,
                    endLine: 70,
                },
            ],
        },
        {
            // A multi-location issue for testing next/previous navigation
            severity: 'HIGH',
            category: 'VISIBILITY_MISMATCH',
            description: 'Multi-location navigation test issue',
            suggestedFix: 'Resolve inconsistencies across artifacts.',
            relatedLocations: [
                {
                    type: 'TESTS_REPOSITORY',
                    filePath: 'src/template/A.java',
                    startLine: 10,
                    endLine: 20,
                },
                {
                    type: 'TESTS_REPOSITORY',
                    filePath: 'src/template/B.java',
                    startLine: 30,
                    endLine: 40,
                },
                {
                    type: 'SOLUTION_REPOSITORY',
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
                    case 'TEMPLATE_REPOSITORY':
                        return CommentThreadLocationType.TEMPLATE_REPO;
                    case 'SOLUTION_REPOSITORY':
                        return CommentThreadLocationType.SOLUTION_REPO;
                    case 'TESTS_REPOSITORY':
                        return CommentThreadLocationType.TEST_REPO;
                    case 'PROBLEM_STATEMENT':
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
            connectAdaptation: vi.fn(),
            adaptationRequests: new Subject<ReviewAdaptationRequest>().asObservable(),
        };
        reviewCommentService.reloadThreads.mockImplementation((onLoaded?: () => void) => onLoaded?.());

        await configureTestBed([{ provide: ExerciseReviewCommentService, useValue: reviewCommentService }]);

        alertService = TestBed.inject(AlertService);
        profileService = TestBed.inject(ProfileService);
        artemisIntelligenceService = TestBed.inject(ArtemisIntelligenceService);
        consistencyCheckService = TestBed.inject(ConsistencyCheckService);

        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;

        comp.exercise.set(createMockExercise({ isAtLeastEditor: true }));

        setCodeEditorContainer(comp, createDefaultContainerStub());
        setEditableInstructions(comp, {
            jumpToLine: vi.fn(),
            clearReviewCommentDrafts: vi.fn(),
        });
        comp.selectTemplateParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectSolutionParticipation = vi.fn().mockResolvedValue(undefined);
        comp.selectTestRepository = vi.fn().mockResolvedValue(undefined);
    });

    afterEach(() => {
        window.history.replaceState({}, '', window.location.href);
        fixture?.destroy();
        vi.clearAllMocks();
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

    it('ignores a stale exercise response after navigating to another exercise', () => {
        const routeParams = new Subject<Record<string, string>>();
        (TestBed.inject(ActivatedRoute) as any).params = routeParams;
        const firstLoad = new Subject<ProgrammingExercise>();
        const secondLoad = new Subject<ProgrammingExercise>();
        vi.spyOn(comp, 'loadExercise').mockImplementation((exerciseId) => (exerciseId === 41 ? firstLoad : secondLoad));
        vi.spyOn(comp, 'saveChangesAndSelectDomain').mockImplementation(() => undefined);

        comp.ngOnInit();
        routeParams.next({ exerciseId: '41', repositoryType: RepositoryType.TESTS, repositoryId: '0' });
        routeParams.next({ exerciseId: '42', repositoryType: RepositoryType.TESTS, repositoryId: '0' });
        secondLoad.next(createMockExercise({ id: 42, title: 'Current exercise' }));
        firstLoad.next(createMockExercise({ id: 41, title: 'Stale exercise' }));

        expect(comp.exercise()!.id).toBe(42);
        expect(comp.exercise()!.title).toBe('Current exercise');
    });

    describe('Consistency Checks', () => {
        const error1 = new ConsistencyCheckError();
        error1.programmingExercise = { id: 42 } as any;
        error1.type = ErrorType.TEMPLATE_BUILD_PLAN_MISSING;

        it('does not run the consistency check for a user below the editor role', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            comp.exercise.set(createMockExercise({ isAtLeastEditor: false }));

            expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');
            comp.checkConsistencies(comp.exercise()!);

            expect(check1Spy).not.toHaveBeenCalled();
        });

        it('runs full consistency check and shows success when no issues', () => {
            const check1Spy = vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            const check2Spy = vi
                .spyOn(artemisIntelligenceService, 'consistencyCheck')
                .mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            const successSpy = vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise()!);

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

            comp.checkConsistencies(comp.exercise()!);

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

            comp.checkConsistencies(comp.exercise()!);

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

            comp.checkConsistencies(comp.exercise()!);

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

            comp.checkConsistencies(comp.exercise()!);
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
            expect(comp.getSeverityIcon('HIGH')).toBe(faCircleExclamation);
            expect(comp.getSeverityIcon('MEDIUM')).toBe(faTriangleExclamation);
            expect(comp.getSeverityIcon('LOW')).toBe(faCircleInfo);
            expect(comp.getSeverityIcon(undefined as any)).toBe(faCircleInfo);
        });

        it('returns right color', () => {
            expect(comp.getSeverityColor('HIGH')).toBe('text-danger');
            expect(comp.getSeverityColor('MEDIUM')).toBe('text-warning');
            expect(comp.getSeverityColor('LOW')).toBe('text-info');
            expect(comp.getSeverityColor(undefined as any)).toBe('text-secondary');
        });

        it('should toggle toolbar and select first issue if none selected', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            expect(comp.showConsistencyIssuesToolbar()).toBe(false);

            comp.toggleConsistencyIssuesToolbar();
            expect(comp.showConsistencyIssuesToolbar()).toBe(true);

            const sorted = comp.sortedIssues();
            expect(comp.selectedIssue()).toEqual(sorted[0]);
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
            expect(comp.selectedIssue()).toEqual(sorted[0]);
        });

        it('should navigate global next', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue.set(sorted[0]);

            const jumpSpy = vi.spyOn(internals(comp), 'jumpToLocation').mockImplementation(() => {});

            // Next step
            comp.navigateGlobal(1);

            expect(comp.selectedIssue()).toBe(sorted[1]);
            expect(jumpSpy).toHaveBeenCalledWith(sorted[1]);

            comp.navigateGlobal(1);
            expect(comp.selectedIssue()).toBe(sorted[2]);
        });

        it('should navigate global previous and wrap around', () => {
            reviewCommentService.threads.set(createConsistencyThreads(mockIssues) as any);
            const sorted = comp.sortedIssues();

            // Start at first issue
            comp.selectedIssue.set(sorted[0]);

            const jumpSpy = vi.spyOn(internals(comp), 'jumpToLocation').mockImplementation(() => {});

            const lastIssue = sorted[sorted.length - 1];

            comp.navigateGlobal(-1);

            expect(comp.selectedIssue()).toBe(lastIssue);
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

        it('does not load a deferred file while its target repository is still initializing', () => {
            const selectedRepository = vi.fn().mockReturnValue(RepositoryType.TEMPLATE);
            getCodeEditorContainer(comp).selectedRepository = selectedRepository;

            internals(comp).navigateToLocation({ targetType: CommentThreadLocationType.SOLUTION_REPO, filePath: 'src/solution/B.java' });
            comp.onEditorLoaded();

            expect(getCodeEditorContainer(comp).selectedFile).toBeUndefined();

            comp.onRepositoryFilesLoaded();

            expect(getCodeEditorContainer(comp).selectedFile).toBeUndefined();

            selectedRepository.mockReturnValue(RepositoryType.SOLUTION);
            comp.onRepositoryFilesLoaded();

            expect(getCodeEditorContainer(comp).selectedFile).toBe('src/solution/B.java');
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
            comp.selectedIssue.set(comp.sortedIssues()[0]);

            vi.spyOn(consistencyCheckService, 'checkConsistencyForProgrammingExercise').mockReturnValue(of([]));
            vi.spyOn(artemisIntelligenceService, 'consistencyCheck').mockReturnValue(of({ timestamp: new Date().toISOString(), issues: [] } as ConsistencyCheckResponse));
            vi.spyOn(alertService, 'success');

            comp.checkConsistencies(comp.exercise()!);

            expect(comp.showConsistencyIssuesToolbar()).toBe(false);
            expect(comp.selectedIssue()).toBeUndefined();
        });
    });

    it('initializes the route domain while generation status is still hydrating', () => {
        setCodeEditorContainer(comp, undefined);
        vi.spyOn(comp as any, 'isProblemStatementEditingLocked').mockReturnValue(true);
        const setDomain = TestBed.inject(DomainService).setDomain as ReturnType<typeof vi.fn>;
        const domain: DomainChange = [DomainType.TEST_REPOSITORY, comp.exercise()!];

        comp.saveChangesAndSelectDomain(domain);

        expect(setDomain).toHaveBeenCalledExactlyOnceWith(domain);
    });

    it('can navigate to another repository while exercise generation locks mutations', async () => {
        vi.spyOn(comp as any, 'isExerciseGenerationActionBlocked').mockReturnValue(true);
        const selectTemplate = vi.spyOn(CodeEditorInstructorBaseContainerComponent.prototype, 'selectTemplateParticipation').mockResolvedValue(true);

        await CodeEditorInstructorAndEditorContainerComponent.prototype.selectTemplateParticipation.call(comp);

        expect(selectTemplate).toHaveBeenCalledOnce();
    });

    it('saves and switches domains normally when exercise generation does not lock editing', () => {
        const onSave = vi.fn();
        setCodeEditorContainer(comp, { actions: () => ({ onSave }) });
        vi.spyOn(comp as any, 'isProblemStatementEditingLocked').mockReturnValue(false);
        const setDomain = TestBed.inject(DomainService).setDomain as ReturnType<typeof vi.fn>;
        const domain: DomainChange = [DomainType.TEST_REPOSITORY, comp.exercise()!];

        comp.saveChangesAndSelectDomain(domain);

        expect(onSave).toHaveBeenCalledOnce();
        expect(setDomain).toHaveBeenCalledExactlyOnceWith(domain);
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Diff Editor', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;

    beforeEach(async () => {
        await configureTestBed();

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise.set(createMockExercise({ problemStatement: 'Original' }));
    });

    afterEach(() => {
        fixture?.destroy();
        window.history.replaceState({}, '', window.location.href);
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
        comp.exercise.set(createMockExercise({ problemStatement: 'Original problem statement', isAtLeastEditor: true }));
    });

    afterEach(() => {
        fixture?.destroy();
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('should delegate inline refinement to service and show diff on success', () => {
        problemStatementService.refineTargeted.mockReturnValue(of({ success: true, content: 'Refined content' }));

        comp.onInlineRefinement({ instruction: 'Improve this', startLine: 1, endLine: 2, startColumn: 1, endColumn: 10 });

        expect(problemStatementService.refineTargeted).toHaveBeenCalledWith(
            comp.exercise(),
            'Original problem statement',
            expect.objectContaining({ instruction: 'Improve this' }),
            expect.any(Function),
        );
        expect(comp.showDiff()).toBe(true);
    });

    it('should preserve refinement prompt when popover hides (prompt is never cleared on dismiss)', () => {
        comp.refinementPrompt.set('Some prompt');
        // The prompt signal should persist since there's no onHide handler clearing it
        expect(comp.refinementPrompt()).toBe('Some prompt');
    });

    it('blocks competing AI mutations while exercise generation is running', () => {
        const consistencyService = TestBed.inject(ConsistencyCheckService);
        (comp as any).generationStartPending.set(true);
        comp.refinementPrompt.set('Improve clarity');

        expect((comp as any).refineBlockedReason()).toBe('artemisApp.review.adaptExercise.runInProgress');
        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.review.adaptExercise.runInProgress');

        comp.submitRefinement();
        comp.onInlineRefinement({ instruction: 'Improve this', startLine: 1, endLine: 2, startColumn: 1, endColumn: 10 });
        comp.checkConsistencies(comp.exercise()!);

        expect(problemStatementService.refineGlobally).not.toHaveBeenCalled();
        expect(problemStatementService.refineTargeted).not.toHaveBeenCalled();
        expect(consistencyService.checkConsistencyForProgrammingExercise).not.toHaveBeenCalled();
    });

    it('blocks refinement and the consistency check for a user below the editor role', () => {
        const consistencyService = TestBed.inject(ConsistencyCheckService);
        comp.exercise.set(createMockExercise({ problemStatement: 'Original problem statement', isAtLeastEditor: false }));
        comp.refinementPrompt.set('Improve clarity');

        expect((comp as any).refineBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');
        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');

        comp.submitRefinement();
        comp.checkConsistencies(comp.exercise()!);

        expect(problemStatementService.refineGlobally).not.toHaveBeenCalled();
        expect(consistencyService.checkConsistencyForProgrammingExercise).not.toHaveBeenCalled();
    });

    it('reports the busy problem-statement operation as the refinement blocker', () => {
        comp.aiOps.isGeneratingOrRefining.set(true);

        expect((comp as any).refineBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.problemStatementBusy');
        expect((comp as any).consistencyBlockedReason()).toBeUndefined();
    });

    it('should delegate global refinement to service and show diff on success', () => {
        problemStatementService.refineGlobally.mockReturnValue(of({ success: true, content: 'Refined content' }));

        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.templateProblemStatement.set('Template');
        comp.aiOps.currentProblemStatement.set('Original problem statement');
        comp.refinementPrompt.set('Improve clarity');

        comp.submitRefinement();

        expect(problemStatementService.refineGlobally).toHaveBeenCalledWith(comp.exercise(), 'Original problem statement', 'Improve clarity', expect.any(Function));
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
        comp.exercise.set(createMockExercise());
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
        const openFile = vi.fn(() => ({ doc: {}, text: { toString: () => '', toJSON: () => '' }, awareness: {} }));

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

        internals(comp).fileSyncService = makeFileSyncStub(stateReplaced$, { doc: {}, text: { toString: () => '', toJSON: () => '' }, awareness: {} });

        const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding').mockImplementation(() => {
            internals(comp).currentFileBinding = [oldBinding, newBinding][bindingCallCount++];
        });

        setCodeEditorContainer(comp, makeContainerStub(model));

        // Load the file — creates the first binding and subscribes to stateReplaced$
        internals(comp).onFileSyncLoad('src/Main.java');
        expect(createFileBindingSpy).toHaveBeenCalledOnce();

        // Emit a state replacement for the same file
        const newText = { toString: () => 'replacement text', toJSON: () => 'replacement text' } as any;
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

        internals(comp).fileSyncService = makeFileSyncStub(stateReplaced$, { doc: {}, text: { toString: () => '', toJSON: () => '' }, awareness: {} });

        const createFileBindingSpy = vi.spyOn(internals(comp), 'createFileBinding').mockImplementation(() => {
            internals(comp).currentFileBinding = binding;
        });

        setCodeEditorContainer(comp, makeContainerStub(model));

        internals(comp).onFileSyncLoad('src/Main.java');

        // Emit for a DIFFERENT file — must be ignored
        stateReplaced$.next({ filePath: 'src/Other.java', doc: {} as any, text: { toString: () => 'other', toJSON: () => 'other' } as any, awareness: {} as any });

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

describe('CodeEditorInstructorAndEditorContainerComponent - Adapt with feedback', () => {
    let fixture: ComponentFixture<CodeEditorInstructorAndEditorContainerComponent>;
    let comp: CodeEditorInstructorAndEditorContainerComponent;
    let generationService: { generate: ReturnType<typeof vi.fn> };
    let attachToJob: ReturnType<typeof vi.fn>;
    let confirm: ReturnType<typeof vi.fn>;
    let openEditorBottomPanel: ReturnType<typeof vi.fn>;
    let selectedIds: WritableSignal<number[]>;
    let reviewCommentService: {
        setExercise: ReturnType<typeof vi.fn>;
        reloadThreads: ReturnType<typeof vi.fn>;
        getSelectedFeedbackThreadIdsForRepository: ReturnType<typeof vi.fn>;
        threads: WritableSignal<any[]>;
        selectThreadAsFeedback: ReturnType<typeof vi.fn>;
        toggleThreadFeedbackSelection: ReturnType<typeof vi.fn>;
        selectedFeedbackThreads: ReturnType<typeof vi.fn>;
        selectedFeedbackThreadIds: WritableSignal<number[]>;
        clearSelectedFeedback: ReturnType<typeof vi.fn>;
        connectAdaptation: ReturnType<typeof vi.fn>;
        adaptationRequests: Observable<ReviewAdaptationRequest>;
        requestAdaptation: (threadId: number) => void;
    };
    /** What the container connected; the mock mirrors the real service's guard so a thread request honours it. */
    let adaptation: ReviewAdaptationAvailability | undefined;
    let adaptationRequests: Subject<ReviewAdaptationRequest>;

    const consistencyThread = (id: number) => ({
        id,
        targetType: CommentThreadLocationType.SOLUTION_REPO,
        filePath: 'src/Solution.java',
        lineNumber: 12,
        outdated: false,
        resolved: false,
        comments: [
            {
                id: id * 10,
                type: CommentType.CONSISTENCY_CHECK,
                createdDate: new Date(2024, 0, 1).toISOString(),
                content: {
                    contentType: CommentContentType.CONSISTENCY_CHECK,
                    severity: 'HIGH',
                    category: 'METHOD_PARAMETER_MISMATCH',
                    text: 'Fix method signature',
                },
            },
        ],
    });

    const userThread = (id: number) => ({
        id,
        targetType: CommentThreadLocationType.SOLUTION_REPO,
        filePath: 'src/Solution.java',
        lineNumber: 15,
        outdated: false,
        resolved: false,
        comments: [
            { id: id * 10, type: CommentType.USER, createdDate: new Date(2024, 0, 1).toISOString(), content: { contentType: CommentContentType.USER, text: 'please rename' } },
        ],
    });

    beforeEach(async () => {
        selectedIds = signal<number[]>([]);
        adaptation = undefined;
        adaptationRequests = new Subject<ReviewAdaptationRequest>();
        reviewCommentService = {
            setExercise: vi.fn(),
            reloadThreads: vi.fn((onLoaded?: () => void) => onLoaded?.()),
            getSelectedFeedbackThreadIdsForRepository: vi.fn(() => []),
            threads: signal([]),
            selectThreadAsFeedback: vi.fn((threadId: number) => selectedIds.update((ids) => (ids.includes(threadId) ? ids : [...ids, threadId]))),
            toggleThreadFeedbackSelection: vi.fn((threadId: number) =>
                selectedIds.update((ids) => (ids.includes(threadId) ? ids.filter((id) => id !== threadId) : [...ids, threadId])),
            ),
            selectedFeedbackThreads: vi.fn(() => reviewCommentService.threads().filter((thread) => selectedIds().includes(thread.id))),
            selectedFeedbackThreadIds: selectedIds,
            clearSelectedFeedback: vi.fn(() => selectedIds.set([])),
            connectAdaptation: vi.fn((availability: ReviewAdaptationAvailability) => (adaptation = availability)),
            adaptationRequests: adaptationRequests.asObservable(),
            requestAdaptation: vi.fn((threadId: number) => {
                if (!adaptation?.offered() || adaptation.blockedReason()) {
                    return;
                }
                const wasAlreadySelected = selectedIds().includes(threadId);
                (reviewCommentService.selectThreadAsFeedback as (threadId: number) => void)(threadId);
                adaptationRequests.next({ threadId, wasAlreadySelected });
            }),
        };
        generationService = { generate: vi.fn(() => of({ jobId: 'job-adapt-1' })) };
        confirm = vi.fn((options) => options.accept?.());

        await configureTestBed([
            { provide: ExerciseReviewCommentService, useValue: reviewCommentService },
            { provide: HyperionExerciseGenerationService, useValue: generationService },
            { provide: TumUiConfirmationService, useValue: { confirm, close: vi.fn() } },
        ]);

        const adaptProfileService = TestBed.inject(ProfileService);
        vi.spyOn(adaptProfileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(adaptProfileService, 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_LOCALCI);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise.set(
            createMockExercise({
                problemStatement: 'Implement the specified behavior and cover all required edge cases.',
                programmingLanguage: ProgrammingLanguage.JAVA,
                isAtLeastEditor: true,
                releaseDate: dayjs().add(1, 'day'),
            }),
        );

        attachToJob = vi.fn();
        openEditorBottomPanel = vi.fn();
        setCodeEditorContainer(comp, { ...createDefaultContainerStub(), openEditorBottomPanel });
        (comp as any).generationActivity = { attachToJob, running: () => false, statusLoading: () => false, statusLoadFailed: () => false };
    });

    afterEach(() => {
        fixture?.destroy();
        window.history.replaceState({}, '', window.location.href);
        vi.clearAllMocks();
    });

    // The specs run with an empty template, so these stand in for what the rendered tum-ui-dialog emits.
    const confirmAdaptDialog = (instructions?: string) => (comp as any).onAdaptDialogConfirmed({ instructions });
    const dismissAdaptDialog = () => {
        comp.adaptDialogVisible.set(false);
        (comp as any).onAdaptDialogHidden();
    };

    it('connects the adaptation availability to the review threads', () => {
        expect(reviewCommentService.connectAdaptation).toHaveBeenCalledOnce();
        expect(adaptation!.offered()).toBe(true);
        expect(adaptation!.blockedReason()).toBeUndefined();
        expect((comp as any).adaptOffered()).toBe(true);
        expect((comp as any).canAdaptNow()).toBe(true);
    });

    it('a thread request selects the thread once, opens the dialog, then dispatches an ADAPT run and attaches it', () => {
        reviewCommentService.threads.set([consistencyThread(9)]);

        reviewCommentService.requestAdaptation(9);
        expect(reviewCommentService.selectThreadAsFeedback).toHaveBeenCalledExactlyOnceWith(9);
        expect(comp.adaptDialogVisible()).toBe(true);

        confirmAdaptDialog('also rename the method');

        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: [9],
        });
        expect(reviewCommentService.clearSelectedFeedback).toHaveBeenCalledOnce();
        expect(selectedIds()).toEqual([]);
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-1', 'ADAPT');
        expect(TestBed.inject(HyperionJobRegistryService).track).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'job-adapt-1', exerciseId: 42, mode: 'ADAPT' }));
        expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/course-management', 1, 'programming-exercises', 42, 'generation']);
    });

    it.each([ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_GRADLE])('supports Java generation for project type %s', (projectType) => {
        // Mutating the exercise in place mirrors production, where the object identity is kept and the change is
        // published through the always-notifying exercise signal.
        comp.exercise.update((exercise) => Object.assign(exercise!, { projectType: projectType as ProjectType | undefined }));

        expect((comp as any).adaptBlockedReason()).toBeUndefined();
        expect((comp as any).canAdaptNow()).toBe(true);
    });

    it.each([
        undefined,
        null,
        ProjectType.MAVEN_MAVEN,
        ProjectType.PLAIN_MAVEN,
        ProjectType.MAVEN_BLACKBOX,
        ProjectType.PLAIN,
        ProjectType.XCODE,
        ProjectType.FACT,
        ProjectType.GCC,
    ])('blocks Java generation for unsupported project type %s', (projectType) => {
        comp.exercise.update((exercise) => Object.assign(exercise!, { projectType }));

        expect((comp as any).adaptOffered()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.unsupportedProjectType');
        expect((comp as any).canAdaptNow()).toBe(false);
        const onCancel = vi.fn();
        (comp as any).openAdaptDialog(onCancel);
        expect(onCancel).toHaveBeenCalledOnce();
        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('names the missing editor role first, before any exercise blocker', () => {
        comp.exercise.set(createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: false, releaseDate: undefined }));

        expect((comp as any).adaptOffered()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');
        expect((comp as any).refineBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');
        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.requiresEditor');
    });

    it('does not select a thread for a user below the editor role', () => {
        comp.exercise.set(createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: false, releaseDate: dayjs().add(1, 'day') }));
        reviewCommentService.threads.set([consistencyThread(9)]);

        reviewCommentService.requestAdaptation(9);

        expect(reviewCommentService.selectThreadAsFeedback).not.toHaveBeenCalled();
        expect(comp.adaptDialogVisible()).toBe(false);
    });

    it('reports the run state as the adapt blocker once the exercise itself qualifies', () => {
        (comp as any).generationActivity = { attachToJob, running: () => true, statusLoading: () => false, statusLoadFailed: () => false };

        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.runInProgress');
        expect((comp as any).refineBlockedReason()).toBe('artemisApp.review.adaptExercise.runInProgress');
        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.review.adaptExercise.runInProgress');
        expect((comp as any).progressLink()).toEqual(['/course-management', 1, 'programming-exercises', 42, 'generation']);
        // The run already shows as the progress link's status dot, so the menu trigger does not spin for it as well.
        expect((comp as any).aiActionsBusy()).toBe(false);
    });

    it('reports a pending start, a pending reload and a failed reload as adapt blockers', () => {
        (comp as any).generationStartPending.set(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.starting');
        (comp as any).generationStartPending.set(false);

        (comp as any).generationRefreshPending.set(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.reloadRequired');
        comp.adaptDialogVisible.set(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.reloadDraftRequired');
        comp.adaptDialogVisible.set(false);
        (comp as any).generationRefreshPending.set(false);

        (comp as any).generationRefreshFailed.set(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.reloadRequired');
        expect((comp as any).progressLink()).toEqual(['/course-management', 1, 'programming-exercises', 42, 'generation']);
    });

    it('reports the busy consistency check as the consistency blocker only', () => {
        vi.spyOn(TestBed.inject(ArtemisIntelligenceService), 'isLoading').mockReturnValue(true);

        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.consistencyCheckBusy');
        expect((comp as any).adaptBlockedReason()).toBeUndefined();
        expect((comp as any).aiActionsBusy()).toBe(true);
        comp.checkConsistencies(comp.exercise()!);
        expect(TestBed.inject(ConsistencyCheckService).checkConsistencyForProgrammingExercise).not.toHaveBeenCalled();
    });

    it('checks for dirty editor state before opening the adaptation dialog', () => {
        setCodeEditorContainer(comp, { canDeactivate: () => false });
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

        (comp as any).openAdaptDialog();

        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.saveChangesFirst');
    });

    it('offers the generation page and explains the lost status while it is unavailable', () => {
        (comp as any).generationActivity = { running: () => false, statusLoading: () => false, statusLoadFailed: () => true };

        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.statusUnavailable');
        expect((comp as any).refineBlockedReason()).toBe('artemisApp.review.adaptExercise.statusUnavailable');
        expect((comp as any).consistencyBlockedReason()).toBe('artemisApp.review.adaptExercise.statusUnavailable');
        expect((comp as any).progressLink()).toEqual(['/course-management', 1, 'programming-exercises', 42, 'generation']);
        expect((comp as any).aiActionsBusy()).toBe(false);
        expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
    });

    it('reports the hydrating status as the adapt blocker', () => {
        (comp as any).generationActivity = { attachToJob, running: () => false, statusLoading: () => true, statusLoadFailed: () => false };

        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.review.adaptExercise.checkingStatus');
        expect((comp as any).aiActionsBusy()).toBe(false);
    });

    it('keeps the editor locked while generation status is hydrating', () => {
        (comp as any).generationActivity = { attachToJob, running: () => false, statusLoading: () => true, statusLoadFailed: () => false };

        expect((comp as any).isExerciseGenerationRunning()).toBe(true);
    });

    it('keeps all exercise editing locked when generation status could not be verified', () => {
        (comp as any).generationActivity = { attachToJob, running: () => false, statusLoading: () => false, statusLoadFailed: () => true };

        expect((comp as any).isExerciseGenerationRunning()).toBe(false);
        expect((comp as any).isProblemStatementEditingLocked()).toBe(true);

        (comp as any).openAdaptDialog();

        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it.each([
        ['status is loading', { statusLoading: true, statusLoadFailed: false }],
        ['status loading failed', { statusLoading: false, statusLoadFailed: true }],
    ])('disables and guards Adapt with feedback while generation %s', (_description, status) => {
        (comp as any).generationActivity = {
            attachToJob,
            running: () => false,
            statusLoading: () => status.statusLoading,
            statusLoadFailed: () => status.statusLoadFailed,
        };

        expect((comp as any).isExerciseGenerationActionBlocked()).toBe(true);
        expect((comp as any).canAdaptNow()).toBe(false);

        reviewCommentService.requestAdaptation(9);
        (comp as any).openAdaptDialog();

        expect(reviewCommentService.selectThreadAsFeedback).not.toHaveBeenCalled();
        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('reloads a clean editor after a persisted generation changes the exercise', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-1', mode: 'GENERATE', liveExerciseChanged: true });

        expect(reloadEditor).toHaveBeenCalledOnce();
        expect((comp as any).generationRefreshPending()).toBe(true);
    });

    it('warns and does not reload when a generation revert arrives while the editor is dirty', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });
        const warning = vi.spyOn(TestBed.inject(AlertService), 'warning');

        TestBed.inject(HyperionGenerationActivityFacade).generationReverted.next('2024-01-01T00:00:00Z');

        expect(reloadEditor).not.toHaveBeenCalled();
        expect(warning).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.refreshBlockedByLocalEdits');
    });

    it('reloads a clean editor exactly once when a generation revert arrives', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false });

        TestBed.inject(HyperionGenerationActivityFacade).generationReverted.next('2024-01-01T00:00:00Z');

        expect(reloadEditor).toHaveBeenCalledOnce();
        expect((comp as any).generationRefreshPending()).toBe(true);
    });

    it('does not double-reload when a generation revert fires twice, guarded by generationRefreshPending rather than a job-id dedup', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false });

        TestBed.inject(HyperionGenerationActivityFacade).generationReverted.next('2024-01-01T00:00:00Z');
        TestBed.inject(HyperionGenerationActivityFacade).generationReverted.next('2024-01-01T00:00:01Z');

        expect(reloadEditor).toHaveBeenCalledOnce();
    });

    it('preserves edits made while generation is running even when the editor was clean at admission', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false });
        (comp as any).openAdaptDialog();
        confirmAdaptDialog('Improve the exercise');

        (comp as any).problemStatementHasUnsavedChanges.set(true);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });
        setEditableInstructions(comp, { unsavedChangesValue: () => true });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-1', mode: 'ADAPT', liveExerciseChanged: true });

        expect(reloadEditor).not.toHaveBeenCalled();
        expect(generationService.generate).toHaveBeenCalledWith(42, { mode: 'ADAPT', prompt: 'Improve the exercise', selectedFeedbackThreadIds: undefined });
        expect((comp as any).generationRefreshPending()).toBe(false);
        expect((comp as any).generationRefreshFailed()).toBe(true);
    });

    it('does not reload while a review comment draft is open', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true, hasReviewCommentDrafts: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false, hasReviewCommentDrafts: () => false });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-1', mode: 'ADAPT', liveExerciseChanged: true });

        expect(reloadEditor).not.toHaveBeenCalled();
        expect((comp as any).generationRefreshFailed()).toBe(true);
    });

    it('does not reload while a problem statement review draft is open', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true, hasReviewCommentDrafts: () => false });
        setEditableInstructions(comp, { unsavedChangesValue: () => false, hasReviewCommentDrafts: () => true });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-1', mode: 'ADAPT', liveExerciseChanged: true });

        expect(reloadEditor).not.toHaveBeenCalled();
        expect((comp as any).generationRefreshFailed()).toBe(true);
    });

    it('does not replay the same job refresh after the resulting page reload', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        const warning = vi.spyOn(TestBed.inject(AlertService), 'warning');
        const completion = { jobId: 'job-1', mode: 'ADAPT' as const, liveExerciseChanged: true };
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });

        (comp as any).onHyperionGenerationCompleted(completion);

        (comp as any).generationRefreshPending.set(false);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });
        (comp as any).onHyperionGenerationCompleted(completion);

        expect(reloadEditor).toHaveBeenCalledOnce();
        expect((comp as any).generationRefreshFailed()).toBe(false);
        expect(warning).not.toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.refreshBlockedByLocalEdits');
    });

    it('preserves and locks local edits instead of reloading stale editor state', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });
        const warning = vi.spyOn(TestBed.inject(AlertService), 'warning');

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-1', mode: 'GENERATE', liveExerciseChanged: true });

        expect(reloadEditor).not.toHaveBeenCalled();
        expect((comp as any).generationRefreshFailed()).toBe(true);
        expect((comp as any).generationRefreshBaselineUnknown()).toBe(true);
        expect((comp as any).isProblemStatementEditingLocked()).toBe(true);
        expect(warning).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.refreshBlockedByLocalEdits');
    });

    it('keeps dirty local edits and the safety lock when reloading the saved exercise is rejected', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        confirm.mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-dirty', mode: 'GENERATE', liveExerciseChanged: true });
        (comp as any).retryHyperionRefresh();

        expect(confirm).toHaveBeenCalledWith(
            expect.objectContaining({
                key: 'hyperionReloadSavedExerciseConfirmation',
                header: 'artemisApp.hyperion.generationActivity.reloadSavedExerciseConfirmHeader',
                message: 'artemisApp.hyperion.generationActivity.reloadSavedExerciseConfirmMessage',
                rejectLabel: 'entity.action.cancel',
                acceptLabel: 'artemisApp.hyperion.generationActivity.reloadSavedExercise',
                acceptSeverity: 'danger',
                accept: expect.any(Function),
            }),
        );

        expect(reloadEditor).not.toHaveBeenCalled();
        expect((comp as any).generationRefreshPending()).toBe(false);
        expect((comp as any).generationRefreshFailed()).toBe(true);
        expect((comp as any).generationRefreshBaselineUnknown()).toBe(true);
        expect((comp as any).isProblemStatementEditingLocked()).toBe(true);
        expect(window.history.state.appliedHyperionGenerationRefresh).toBeUndefined();
    });

    it('accepts reloading the saved exercise while dirty and marks that exact job before the full reload', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        confirm.mockImplementation(() => undefined);
        const allowNextUnloadWithoutConfirmation = vi.fn();
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false, allowNextUnloadWithoutConfirmation });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-dirty', mode: 'ADAPT', liveExerciseChanged: true });
        (comp as any).retryHyperionRefresh();
        confirm.mock.calls.at(-1)![0].accept();

        expect(window.history.state.appliedHyperionGenerationRefresh).toEqual({ exerciseId: 42, jobId: 'job-dirty' });
        expect((comp as any).generationRefreshPending()).toBe(true);
        expect(allowNextUnloadWithoutConfirmation).toHaveBeenCalledOnce();
        expect(reloadEditor).toHaveBeenCalledOnce();
    });

    it('reloads exactly once when the saved-exercise confirmation accept callback is repeated', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        confirm.mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => false, hasCleanRepositoryState: () => false });

        (comp as any).onHyperionGenerationCompleted({ jobId: 'job-dirty', mode: 'GENERATE', liveExerciseChanged: true });
        (comp as any).retryHyperionRefresh();
        const accept = confirm.mock.calls.at(-1)![0].accept;

        accept();
        accept();

        expect(reloadEditor).toHaveBeenCalledOnce();
        expect(window.history.state.appliedHyperionGenerationRefresh).toEqual({ exerciseId: 42, jobId: 'job-dirty' });
    });

    it('does not refresh editor content after a rejected generation completes', () => {
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults');

        (comp as any).onHyperionGenerationCompleted({
            jobId: 'job-1',
            mode: 'ADAPT',
            verdict: { mechanicallyVerified: false, solutionPassed: false, templateFailed: true, testCount: 2 },
        });

        expect(loadSpy).not.toHaveBeenCalled();
    });

    it('reloads review threads without refreshing editor content after a needs-review generation', () => {
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults');

        (comp as any).onHyperionGenerationCompleted({
            jobId: 'job-1',
            mode: 'GENERATE',
            completionStatus: 'NEEDS_REVIEW',
            verdict: { mechanicallyVerified: false, solutionPassed: false, templateFailed: true, testCount: 2 },
            liveExerciseChanged: false,
        });

        expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        expect(loadSpy).not.toHaveBeenCalled();
    });

    it('reloads review threads and the clean editor when a saved generation needs instructor review', () => {
        const reloadEditor = vi.spyOn(comp as any, 'reloadEditor').mockImplementation(() => undefined);
        setCodeEditorContainer(comp, { canDeactivate: () => true, hasCleanRepositoryState: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => false });

        (comp as any).onHyperionGenerationCompleted({
            jobId: 'job-1',
            mode: 'GENERATE',
            completionStatus: 'NEEDS_REVIEW',
            liveExerciseChanged: true,
        });

        expect(reviewCommentService.reloadThreads).toHaveBeenCalledOnce();
        expect(reloadEditor).toHaveBeenCalledOnce();
    });

    it('explains the active-run restriction and restores adaptation when the run finishes', () => {
        const running = signal(true);
        vi.spyOn(comp['generationActivity'], 'running').mockImplementation(running);
        expect(comp['adaptBlockedReason']()).toBe('artemisApp.review.adaptExercise.runInProgress');
        comp['openAdaptDialog']();
        expect(comp.adaptDialogVisible()).toBe(false);
        running.set(false);
        expect(comp['adaptBlockedReason']()).toBeUndefined();
        comp['openAdaptDialog']();
        expect(comp.adaptDialogVisible()).toBe(true);
    });

    it('keeps the dialog open if a run starts between opening and confirmation', () => {
        const running = signal(false);
        vi.spyOn(comp['generationActivity'], 'running').mockImplementation(running);
        comp['openAdaptDialog']();
        running.set(true);
        confirmAdaptDialog('Do not lose this request');
        expect(comp.adaptDialogVisible()).toBe(true);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('keeps the dialog and selected feedback on request failure and closes only after a successful retry', () => {
        reviewCommentService.threads.set([userThread(7)]);
        comp['openAdaptDialog']();
        const pending = new Subject<{ jobId: string }>();
        generationService.generate.mockReturnValueOnce(pending);
        comp['onAdaptDialogConfirmed']({ instructions: 'Clarify examples', selectedFeedbackThreadIds: [7] });
        expect(comp.adaptDialogVisible()).toBe(true);
        expect(comp['generationStartPending']()).toBe(true);
        comp['onAdaptDialogConfirmed']({ instructions: 'Duplicate', selectedFeedbackThreadIds: [] });
        expect(generationService.generate).toHaveBeenCalledTimes(1);
        pending.error(new Error('Unavailable'));
        expect(comp.adaptDialogVisible()).toBe(true);
        expect(comp.adaptSubmissionError()).toBe('artemisApp.review.adaptExercise.startFailed');
        expect(selectedIds()).toEqual([7]);
        comp['onAdaptDialogConfirmed']({ instructions: 'Clarify examples', selectedFeedbackThreadIds: [7] });
        expect(generationService.generate).toHaveBeenLastCalledWith(42, { mode: 'ADAPT', prompt: 'Clarify examples', selectedFeedbackThreadIds: [7] });
        expect(comp.adaptDialogVisible()).toBe(false);
        expect(comp.adaptSubmissionError()).toBeUndefined();
    });

    it('does not silently reload away a draft when another run saves changes', () => {
        const reloadEditor = vi.fn();
        comp['reloadEditor'] = reloadEditor;
        comp['openAdaptDialog']();
        comp['onHyperionGenerationCompleted']({ jobId: 'other-run', mode: 'ADAPT', liveExerciseChanged: true });
        expect(reloadEditor).not.toHaveBeenCalled();
        expect(comp.adaptDialogVisible()).toBe(true);
        expect(comp['adaptBlockedReason']()).toBe('artemisApp.review.adaptExercise.reloadDraftRequired');
        confirmAdaptDialog('My instructions');
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it.each([
        ['statusLoading', 'checkingStatus'],
        ['statusLoadFailed', 'statusUnavailable'],
    ] as const)('explains %s without submitting an adaptation', (state, reason) => {
        vi.spyOn(comp['generationActivity'], state).mockReturnValue(true);
        expect(comp['adaptBlockedReason']()).toBe('artemisApp.review.adaptExercise.' + reason);
        comp['openAdaptDialog']();
        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it.each(['generationCapacityUnavailable', 'exerciseGenerationRunning'])('explains %s while retaining the feedback for retry', (errorKey) => {
        generationService.generate.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorKey } })));
        reviewCommentService.threads.set([userThread(7)]);
        comp['openAdaptDialog']();
        comp['onAdaptDialogConfirmed']({ instructions: 'Keep my request', selectedFeedbackThreadIds: [7] });
        expect(comp.adaptDialogVisible()).toBe(true);
        expect(comp.adaptSubmissionError()).toBe('error.' + errorKey);
        expect(selectedIds()).toEqual([7]);
    });

    it('offers unselected instructor comments in the dialog and submits only the chosen threads', () => {
        reviewCommentService.threads.set([userThread(7), userThread(8)]);
        selectedIds.set([]);
        comp['openAdaptDialog']();
        expect(comp.adaptDialogFindings().map((finding) => finding.threadId)).toEqual([7, 8]);
        expect(comp.adaptDialogSelectedIds()).toEqual([]);
        comp['onAdaptDialogConfirmed']({ selectedFeedbackThreadIds: [8] });
        expect(generationService.generate).toHaveBeenCalledWith(42, { mode: 'ADAPT', prompt: undefined, selectedFeedbackThreadIds: [8] });
    });

    it('includes instructor feedback but excludes resolved and outdated threads from adaptation', () => {
        const resolvedConsistencyThread = { ...consistencyThread(9), resolved: true };
        const outdatedConsistencyThread = { ...consistencyThread(10), outdated: true };
        const activeConsistencyThread = consistencyThread(11);
        reviewCommentService.threads.set([userThread(7), resolvedConsistencyThread, outdatedConsistencyThread, activeConsistencyThread]);
        selectedIds.set([7, 9, 10, 11]);

        expect(comp.selectedAdaptFeedbackCount()).toBe(2);

        (comp as any).openAdaptDialog();
        expect(comp.adaptDialogFindings().map((finding) => finding.description)).toEqual(['please rename', 'Fix method signature']);

        confirmAdaptDialog('also rename the method');

        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: [7, 11],
        });
    });

    it('openAdaptDialog with no selected threads dispatches an ADAPT run with undefined ids', () => {
        (comp as any).openAdaptDialog();
        confirmAdaptDialog('also rename the method');

        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: undefined,
        });
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-1', 'ADAPT');
        expect(TestBed.inject(HyperionJobRegistryService).track).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'job-adapt-1', exerciseId: 42, mode: 'ADAPT' }));
    });

    it('does not dispatch a run when the adapt dialog is dismissed', () => {
        (comp as any).openAdaptDialog();
        dismissAdaptDialog();

        expect(generationService.generate).not.toHaveBeenCalled();
        expect(attachToJob).not.toHaveBeenCalled();
    });

    it('a thread request rolls back a new preview selection when the dialog is dismissed', () => {
        reviewCommentService.threads.set([consistencyThread(9)]);

        reviewCommentService.requestAdaptation(9);
        dismissAdaptDialog();

        expect(reviewCommentService.toggleThreadFeedbackSelection).toHaveBeenCalledExactlyOnceWith(9);
        expect(selectedIds()).toEqual([]);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('a thread request keeps a selection that existed before the dialog when it is dismissed', () => {
        reviewCommentService.threads.set([consistencyThread(9)]);
        selectedIds.set([9]);

        adaptationRequests.next({ threadId: 9, wasAlreadySelected: true });
        expect(comp.adaptDialogVisible()).toBe(true);
        dismissAdaptDialog();

        expect(reviewCommentService.toggleThreadFeedbackSelection).not.toHaveBeenCalled();
        expect(selectedIds()).toEqual([9]);
    });

    it('keeps the preview selection when the adapt dialog is closed programmatically rather than dismissed', () => {
        reviewCommentService.threads.set([consistencyThread(9)]);
        reviewCommentService.requestAdaptation(9);

        (comp as any).invalidateHyperionLifecycleState();
        (comp as any).onAdaptDialogHidden();

        expect(comp.adaptDialogVisible()).toBe(false);
        expect(reviewCommentService.toggleThreadFeedbackSelection).not.toHaveBeenCalled();
        expect(selectedIds()).toEqual([9]);
    });

    it('closes the adapt dialog on destruction and ignores a late confirmation', () => {
        (comp as any).openAdaptDialog();
        fixture.destroy();

        confirmAdaptDialog('late adaptation');

        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('does not dispatch adaptation when the dialog is confirmed after navigating to another exercise', () => {
        (comp as any).openAdaptDialog();
        (comp as any).invalidateHyperionLifecycleState();
        comp.exercise.set(createMockExercise({ id: 84 }));

        confirmAdaptDialog('late adaptation');

        expect(comp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('shows an inline error without a duplicate toast when starting the ADAPT run fails', () => {
        generationService.generate.mockReturnValue(throwError(() => new Error('boom')));
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');

        (comp as any).openAdaptDialog();
        confirmAdaptDialog('also rename the method');

        expect(generationService.generate).toHaveBeenCalledOnce();
        expect(attachToJob).not.toHaveBeenCalled();
        expect(errorSpy).not.toHaveBeenCalled();
        expect(comp.adaptSubmissionError()).toBe('artemisApp.review.adaptExercise.startFailed');
    });

    it('does not start duplicate ADAPT runs while the start request is pending', () => {
        const pending = new Subject<{ jobId: string }>();
        generationService.generate.mockReturnValue(pending);

        (comp as any).startAdaptation('tighten tests');
        (comp as any).startAdaptation('tighten tests');

        expect(generationService.generate).toHaveBeenCalledOnce();
        pending.next({ jobId: 'job-adapt-pending' });
        pending.complete();
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-pending', 'ADAPT');
    });

    it('ignores an adaptation start response after navigating to another exercise', () => {
        const pending = new Subject<{ jobId: string }>();
        generationService.generate.mockReturnValue(pending);

        (comp as any).startAdaptation('tighten tests');
        comp.exercise.set(createMockExercise({ id: 84 }));
        pending.next({ jobId: 'adapt-for-previous-exercise' });
        pending.complete();

        expect(reviewCommentService.clearSelectedFeedback).not.toHaveBeenCalled();
        expect(attachToJob).not.toHaveBeenCalled();
        expect(openEditorBottomPanel).not.toHaveBeenCalled();
    });

    it('ignores an adaptation start failure after navigating to another exercise', () => {
        const pending = new Subject<{ jobId: string }>();
        generationService.generate.mockReturnValue(pending);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).startAdaptation('tighten tests');
        comp.exercise.set(createMockExercise({ id: 84 }));
        pending.error(new Error('late failure'));

        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('does not start ADAPT when local changes are unsaved', () => {
        setCodeEditorContainer(comp, { canDeactivate: () => false });
        selectedIds.set([9]);
        const alertService = TestBed.inject(AlertService);
        const warningSpy = vi.spyOn(alertService, 'warning');

        (comp as any).startAdaptation('tighten tests');

        expect(generationService.generate).not.toHaveBeenCalled();
        expect(reviewCommentService.clearSelectedFeedback).not.toHaveBeenCalled();
        expect(selectedIds()).toEqual([9]);
        expect(attachToJob).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.saveChangesFirst');
    });

    it('does not offer adaptation when whole-exercise generation is disabled', () => {
        const jenkinsProfileService = TestBed.inject(ProfileService);
        vi.spyOn(jenkinsProfileService, 'isModuleFeatureActive').mockImplementation((feature) => feature === MODULE_FEATURE_HYPERION);

        const jenkinsFixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        const jenkinsComp = jenkinsFixture.componentInstance;
        jenkinsComp.exercise.set(createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true }));
        const jenkinsAttach = vi.fn();
        (jenkinsComp as any).generationActivity = { attachToJob: jenkinsAttach };

        expect((jenkinsComp as any).hyperionEnabled).toBe(true);
        expect((jenkinsComp as any).hyperionGenerationSupported).toBe(false);
        expect((jenkinsComp as any).adaptOffered()).toBe(false);
        expect((jenkinsComp as any).generationSupported()).toBe(false);
        expect((jenkinsComp as any).canAdaptNow()).toBe(false);

        (jenkinsComp as any).openAdaptDialog();
        expect(jenkinsComp.adaptDialogVisible()).toBe(false);
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(jenkinsAttach).not.toHaveBeenCalled();
        jenkinsFixture.destroy();
    });

    it('keeps retained activity visible but explains why no new generation is offered after release or participation', () => {
        const localCiProfileService = TestBed.inject(ProfileService);
        vi.spyOn(localCiProfileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(localCiProfileService, 'isProfileActive').mockReturnValue(true);

        comp.exercise.set(createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, releaseDate: dayjs().subtract(1, 'minute') }));
        expect((comp as any).generationSupported()).toBe(true);
        expect((comp as any).adaptOffered()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.released');
        expect((comp as any).canAdaptNow()).toBe(false);

        comp.exercise.set(createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, releaseDate: undefined }));
        expect((comp as any).generationSupported()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.noReleaseDate');

        const unreleased = { programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, releaseDate: dayjs().add(1, 'day') };
        comp.exercise.set(createMockExercise({ ...unreleased, studentParticipations: [{} as any] }));
        expect((comp as any).generationSupported()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.studentParticipations');

        comp.exercise.set(createMockExercise({ ...unreleased, numberOfParticipations: 1 }));
        expect((comp as any).generationSupported()).toBe(true);
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.studentParticipations');

        comp.exercise.set(createMockExercise({ ...unreleased, studentParticipations: [], numberOfParticipations: 1 }));
        expect((comp as any).adaptBlockedReason()).toBe('artemisApp.hyperion.generation.blocker.studentParticipations');
        expect((comp as any).canAdaptNow()).toBe(false);
    });
});
