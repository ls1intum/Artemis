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
import { Subject, of, throwError } from 'rxjs';
import { FileSyncState } from 'app/exercise/synchronization/services/code-editor-file-sync.service';
import { CodeEditorInstructorAndEditorContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-and-editor-container.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CodeEditorRepositoryFileService, CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
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
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { ExerciseEditorSyncService } from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { DialogService } from 'primeng/dynamicdialog';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { ConfirmationService } from 'primeng/api';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';

const AUTO_START_EXERCISE_GENERATION_STATE = 'autoStartExerciseGeneration';

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
    actions?: () => { executeRefresh: ReturnType<typeof vi.fn>; onSave: ReturnType<typeof vi.fn> };
    canDeactivate?: () => boolean;
    selectedFile?: string;
    selectedRepository?: ReturnType<typeof vi.fn>;
    problemStatementIdentifier?: string;
    jumpToLine?: ReturnType<typeof vi.fn>;
    initializeProperties?: ReturnType<typeof vi.fn>;
    monacoEditor?: () => any;
    openEditorBottomPanel?: ReturnType<typeof vi.fn>;
}

function setCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent, stub: CodeEditorContainerStub | undefined): void {
    internals(comp).codeEditorContainer = (() => stub) as unknown as Signal<any>;
}

function getCodeEditorContainer(comp: CodeEditorInstructorAndEditorContainerComponent): any {
    return internals(comp).codeEditorContainer();
}

function setEditableInstructions(comp: CodeEditorInstructorAndEditorContainerComponent, stub: any): void {
    internals(comp).editableInstructions = (() => stub) as unknown as Signal<any>;
}

function createDefaultContainerStub(): CodeEditorContainerStub {
    const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
    const monacoEditor = { clearReviewCommentDrafts: vi.fn() };
    return {
        actions: () => actions,
        canDeactivate: () => true,
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
        { provide: DialogService, useValue: { open: vi.fn(() => ({ onClose: of({ confirmed: true }) })) } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
        { provide: CodeEditorRepositoryService, useValue: { pull: vi.fn(() => of(void 0)) } },
        { provide: CodeEditorRepositoryFileService, useValue: { getRepositoryContent: vi.fn(() => of({})) } },
        { provide: TranslateService, useClass: MockTranslateService },
        { provide: ConsistencyCheckService, useValue: { checkConsistencyForProgrammingExercise: vi.fn() } },
        { provide: ArtemisIntelligenceService, useValue: { consistencyCheck: vi.fn(), isLoading: () => false } },
        { provide: ExerciseEditorSyncService, useValue: { connect: vi.fn(), disconnect: vi.fn(), subscribeToUpdates: vi.fn(() => of()) } },
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

        comp.exercise = createMockExercise();

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

            comp.checkConsistencies(comp.exercise!);

            expect(comp.showConsistencyIssuesToolbar()).toBe(false);
            expect(comp.selectedIssue()).toBeUndefined();
        });
    });
});

describe('CodeEditorInstructorAndEditorContainerComponent - Diff Editor', () => {
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
    let dialogOpen: ReturnType<typeof vi.fn>;
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
    };

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
        };
        generationService = { generate: vi.fn(() => of({ jobId: 'job-adapt-1' })) };
        dialogOpen = vi.fn(() => ({ onClose: of({ instructions: 'also rename the method' }) }));
        confirm = vi.fn((options) => options.accept?.());

        await configureTestBed([
            { provide: ExerciseReviewCommentService, useValue: reviewCommentService },
            { provide: HyperionExerciseGenerationService, useValue: generationService },
            { provide: DialogService, useValue: { open: dialogOpen } },
            { provide: ConfirmationService, useValue: { confirm } },
        ]);

        const adaptProfileService = TestBed.inject(ProfileService);
        vi.spyOn(adaptProfileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(adaptProfileService, 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_LOCALCI);

        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({
            problemStatement: 'Implement the specified behavior and cover all required edge cases.',
            programmingLanguage: ProgrammingLanguage.JAVA,
            isAtLeastEditor: true,
            releaseDate: dayjs().add(1, 'day'),
        });

        attachToJob = vi.fn();
        openEditorBottomPanel = vi.fn();
        setCodeEditorContainer(comp, { ...createDefaultContainerStub(), openEditorBottomPanel });
        (comp as any).generationActivity = () => ({ attachToJob, running: () => false, statusLoading: () => false, statusLoadFailed: () => false });
    });

    afterEach(() => {
        fixture?.destroy();
        vi.clearAllMocks();
    });

    it('adaptFromThread selects the thread once, opens the dialog, then dispatches an ADAPT run and attaches it', () => {
        reviewCommentService.threads.set([consistencyThread(9)]);

        (comp as any).adaptFromThread(9);

        expect(reviewCommentService.selectThreadAsFeedback).toHaveBeenCalledExactlyOnceWith(9);
        expect(dialogOpen).toHaveBeenCalledOnce();
        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: [9],
        });
        expect(reviewCommentService.clearSelectedFeedback).toHaveBeenCalledOnce();
        expect(selectedIds()).toEqual([]);
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-1', 'ADAPT');
        expect(openEditorBottomPanel).toHaveBeenCalledOnce();
    });

    it('confirms automatic persistence and review responsibilities before manual generation', () => {
        (comp as any).startGeneration();

        expect(confirm).toHaveBeenCalledWith(
            expect.objectContaining({
                key: 'hyperionGenerateConfirmation',
                defaultFocus: 'reject',
                accept: expect.any(Function),
            }),
        );
        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, { mode: 'GENERATE' });
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-1', 'GENERATE');
        expect(openEditorBottomPanel).toHaveBeenCalledOnce();
    });

    it('auto-starts creation generation after the activity status finishes loading', async () => {
        fixture.destroy();
        window.history.replaceState({ [AUTO_START_EXERCISE_GENERATION_STATE]: true }, '');
        fixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        comp = fixture.componentInstance;
        comp.exercise = createMockExercise({
            problemStatement: 'Implement the specified behavior and cover all required edge cases.',
            programmingLanguage: ProgrammingLanguage.JAVA,
            projectType: ProjectType.PLAIN_GRADLE,
            isAtLeastEditor: true,
            releaseDate: dayjs().add(1, 'day'),
        });
        setCodeEditorContainer(comp, createDefaultContainerStub());
        const activity = signal<any | undefined>(undefined);
        (comp as any).generationActivity = activity;

        fixture.detectChanges();
        expect(generationService.generate).not.toHaveBeenCalled();

        activity.set({ attachToJob, running: () => false, statusLoading: () => false, statusLoadFailed: () => false });
        fixture.detectChanges();
        await fixture.whenStable();

        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, { mode: 'GENERATE' });
        window.history.replaceState({}, '');
    });

    it.each(['', '   ', 'x'.repeat(39), `  ${'x'.repeat(39)}  `])('blocks manual generation when the meaningful specification is %j', (problemStatement) => {
        comp.exercise.problemStatement = problemStatement;
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

        (comp as any).startGeneration();

        expect(confirm).not.toHaveBeenCalled();
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.meaningfulSpecRequired');
    });

    it('allows manual generation at the 40-character meaningful specification boundary', () => {
        comp.exercise.problemStatement = 'x'.repeat(40);

        (comp as any).startGeneration();

        expect(confirm).toHaveBeenCalledOnce();
        expect(generationService.generate).toHaveBeenCalledOnce();
    });

    it('blocks creation auto-start when navigation state has no meaningful specification', () => {
        comp.exercise.problemStatement = '';
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

        (comp as any).startGeneration(true);

        expect(confirm).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.meaningfulSpecRequired');
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it.each([undefined, null, ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE])(
        'supports Java generation for project type %s',
        (projectType) => {
            comp.exercise.projectType = projectType as ProjectType | undefined;

            expect((comp as any).canGenerateExercise()).toBe(true);
        },
    );

    it.each([ProjectType.MAVEN_BLACKBOX, ProjectType.PLAIN, ProjectType.XCODE, ProjectType.FACT, ProjectType.GCC])(
        'blocks Java generation for unsupported project type %s',
        (projectType) => {
            comp.exercise.projectType = projectType;

            expect((comp as any).canGenerateExercise()).toBe(false);
        },
    );

    it('checks for dirty editor state before opening the adaptation dialog', () => {
        setCodeEditorContainer(comp, { canDeactivate: () => false });
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

        (comp as any).openAdaptDialog();

        expect(dialogOpen).not.toHaveBeenCalled();
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it('reports edits made while the generation confirmation is open instead of silently doing nothing', () => {
        confirm.mockImplementation(() => undefined);
        const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');
        (comp as any).startGeneration();
        setCodeEditorContainer(comp, { canDeactivate: () => false });

        confirm.mock.calls[0][0].accept();

        expect(generationService.generate).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it.each([
        ['solution', 'solution/src/main/Solution.java', CommentThreadLocationType.SOLUTION_REPO, 'src/main/Solution.java'],
        ['template', 'template/src/main/Template.java', CommentThreadLocationType.TEMPLATE_REPO, 'src/main/Template.java'],
        ['tests', 'tests/src/test/ExerciseTest.java', CommentThreadLocationType.TEST_REPO, 'src/test/ExerciseTest.java'],
    ] as const)('navigates a persisted %s snapshot through the authoritative editor', (repo, path, targetType, filePath) => {
        const navigateSpy = vi.spyOn(internals(comp) as any, 'navigateToLocation');
        (comp as any).generationActivity = () => ({ canNavigateSnapshots: () => true });

        (comp as any).onHyperionSnapshotSelected({ repo, path });

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith({ targetType, filePath });
    });

    it('does not fake navigation for an unknown other snapshot', () => {
        const navigateSpy = vi.spyOn(internals(comp) as any, 'navigateToLocation');
        (comp as any).generationActivity = () => ({ canNavigateSnapshots: () => true });

        (comp as any).onHyperionSnapshotSelected({ repo: 'other', path: 'notes.txt' });

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('opens a persisted problem statement snapshot in the authoritative problem editor', () => {
        const navigateSpy = vi.spyOn(internals(comp) as any, 'navigateToLocation');
        (comp as any).generationActivity = () => ({ canNavigateSnapshots: () => true });

        (comp as any).onHyperionSnapshotSelected({ repo: 'other', path: 'problem-statement.md' });

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith({ targetType: CommentThreadLocationType.PROBLEM_STATEMENT, filePath: 'problem-statement.md' });
    });

    it('does not navigate a snapshot before the activity is terminal', () => {
        const navigateSpy = vi.spyOn(internals(comp) as any, 'navigateToLocation');
        (comp as any).generationActivity = () => ({ canNavigateSnapshots: () => false });

        (comp as any).onHyperionSnapshotSelected({ repo: 'solution', path: 'solution/src/Main.java' });

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('opens problem statement version history from the saved-change review', () => {
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        (comp as any).onHyperionReviewRequested({ target: 'problem-statement', jobId: 'job-42' });

        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 1, 'programming-exercises', 42, 'version-history']);
    });

    it('opens the exact Hyperion repository commit instead of assuming the latest commit', () => {
        const historyService = TestBed.inject(ProgrammingExerciseParticipationService);
        const retrieveSpy = vi.spyOn(historyService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(
            of([
                { hash: 'newer-unrelated', message: 'Manual follow-up' },
                { hash: 'similar-job', message: 'Generate exercise with Hyperion (job-420)' },
                { hash: 'hyperion-hash', message: 'Generate exercise with Hyperion (job-42)' },
            ]),
        );
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        (comp as any).onHyperionReviewRequested({ target: 'solution', jobId: 'job-42' });

        expect(retrieveSpy).toHaveBeenCalledExactlyOnceWith(42, RepositoryType.SOLUTION);
        expect(navigateSpy).toHaveBeenCalledExactlyOnceWith([
            '/course-management',
            1,
            'programming-exercises',
            42,
            'repository',
            RepositoryType.SOLUTION,
            'commit-history',
            'hyperion-hash',
        ]);
    });

    it('reports unavailable repository history without navigating', () => {
        const historyService = TestBed.inject(ProgrammingExerciseParticipationService);
        vi.spyOn(historyService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(of([{ message: 'Generate exercise with Hyperion (job-42)' }]));
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'tests', jobId: 'job-42' });

        expect(navigateSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
    });

    it('rejects ambiguous exact Hyperion commit matches', () => {
        const historyService = TestBed.inject(ProgrammingExerciseParticipationService);
        vi.spyOn(historyService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(
            of([
                { hash: 'first', message: 'Generate exercise with Hyperion (job-42)' },
                { hash: 'second', message: 'Generate exercise with Hyperion (job-42)' },
            ]),
        );
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'solution', jobId: 'job-42' });

        expect(navigateSpy).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
    });

    it('keeps repository review deduplicated until navigation completes and reports a false result', async () => {
        const historyService = TestBed.inject(ProgrammingExerciseParticipationService);
        const retrieveSpy = vi
            .spyOn(historyService, 'retrieveCommitHistoryForTemplateSolutionOrTests')
            .mockReturnValue(of([{ hash: 'hyperion-hash', message: 'Generate exercise with Hyperion (job-42)' }]));
        let resolveNavigation!: (result: boolean) => void;
        const navigation = new Promise<boolean>((resolve) => (resolveNavigation = resolve));
        vi.spyOn(TestBed.inject(Router), 'navigate').mockReturnValue(navigation);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'solution', jobId: 'job-42' });
        (comp as any).onHyperionReviewRequested({ target: 'solution', jobId: 'job-42' });
        expect(retrieveSpy).toHaveBeenCalledOnce();

        resolveNavigation(false);
        await navigation;
        await Promise.resolve();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
        (comp as any).onHyperionReviewRequested({ target: 'solution', jobId: 'job-42' });
        expect(retrieveSpy).toHaveBeenCalledTimes(2);
    });

    it('reports failed problem-statement navigation', async () => {
        vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(false);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'problem-statement', jobId: 'job-42' });
        await Promise.resolve();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
    });

    it('deduplicates problem-statement review while navigation is pending', () => {
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockReturnValue(new Promise<boolean>(() => undefined));

        (comp as any).onHyperionReviewRequested({ target: 'problem-statement', jobId: 'job-42' });
        (comp as any).onHyperionReviewRequested({ target: 'problem-statement', jobId: 'job-42' });

        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('reports rejected problem-statement navigation', async () => {
        vi.spyOn(TestBed.inject(Router), 'navigate').mockRejectedValue(new Error('routing failed'));
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'problem-statement', jobId: 'job-42' });
        await Promise.resolve();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
    });

    it('does not navigate when repository history arrives after editor destruction', () => {
        const history = new Subject<any[]>();
        vi.spyOn(TestBed.inject(ProgrammingExerciseParticipationService), 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(history);
        const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');

        (comp as any).onHyperionReviewRequested({ target: 'tests', jobId: 'job-42' });
        fixture.destroy();
        history.next([{ hash: 'hyperion-hash', message: 'Generate exercise with Hyperion (job-42)' }]);

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('ignores repository navigation completion after editor destruction', async () => {
        vi.spyOn(TestBed.inject(ProgrammingExerciseParticipationService), 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(
            of([{ hash: 'hyperion-hash', message: 'Generate exercise with Hyperion (job-42)' }]),
        );
        let resolveNavigation!: (result: boolean) => void;
        const navigation = new Promise<boolean>((resolve) => (resolveNavigation = resolve));
        vi.spyOn(TestBed.inject(Router), 'navigate').mockReturnValue(navigation);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'tests', jobId: 'job-42' });
        fixture.destroy();
        resolveNavigation(false);
        await navigation;
        await Promise.resolve();

        expect(errorSpy).not.toHaveBeenCalled();
        expect((comp as any).reviewRequestsInFlight.size).toBe(0);
    });

    it('reports repository history request failures and ignores duplicate in-flight clicks', () => {
        const history = new Subject<any[]>();
        const historyService = TestBed.inject(ProgrammingExerciseParticipationService);
        const retrieveSpy = vi.spyOn(historyService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(history);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

        (comp as any).onHyperionReviewRequested({ target: 'template', jobId: 'job-42' });
        (comp as any).onHyperionReviewRequested({ target: 'template', jobId: 'job-42' });
        expect(retrieveSpy).toHaveBeenCalledOnce();

        history.error(new Error('network'));

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.reviewUnavailable');
    });

    it('reopens Hyperion from the AI toolbar while status is unavailable', () => {
        (comp as any).generationActivity = () => ({ running: () => false, statusLoading: () => false, statusLoadFailed: () => true });

        (comp as any).onAiToolbarClick({} as Event, { toggle: vi.fn() });

        expect(openEditorBottomPanel).toHaveBeenCalledOnce();
    });

    it('startGeneration is blocked while another run is active or start request is pending', () => {
        const pending = new Subject<{ jobId: string }>();
        generationService.generate.mockReturnValue(pending);

        (comp as any).startGeneration();
        (comp as any).startGeneration();

        expect(generationService.generate).toHaveBeenCalledOnce();
        expect(attachToJob).not.toHaveBeenCalled();

        pending.next({ jobId: 'job-generate-1' });
        pending.complete();
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-generate-1', 'GENERATE');

        (comp as any).generationActivity = () => ({ attachToJob, running: () => true, statusLoading: () => false, statusLoadFailed: () => false });

        (comp as any).startGeneration();

        expect(generationService.generate).toHaveBeenCalledOnce();
    });

    it('keeps the editor locked while generation status is hydrating', () => {
        (comp as any).generationActivity = () => ({ attachToJob, running: () => false, statusLoading: () => true, statusLoadFailed: () => false });

        expect((comp as any).isExerciseGenerationRunning()).toBe(true);
    });

    it('keeps AI actions blocked without locking manual editing when generation status could not be verified', () => {
        (comp as any).generationActivity = () => ({ attachToJob, running: () => false, statusLoading: () => false, statusLoadFailed: () => true });

        expect((comp as any).isExerciseGenerationRunning()).toBe(false);

        (comp as any).startGeneration();

        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('startGeneration is blocked when local repository changes are unsaved', () => {
        setCodeEditorContainer(comp, { canDeactivate: () => false });
        const alertService = TestBed.inject(AlertService);
        const warningSpy = vi.spyOn(alertService, 'warning');

        (comp as any).startGeneration();

        expect(generationService.generate).not.toHaveBeenCalled();
        expect(attachToJob).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it('startGeneration is blocked when local problem statement changes are unsaved', () => {
        setCodeEditorContainer(comp, { canDeactivate: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => true });
        const alertService = TestBed.inject(AlertService);
        const warningSpy = vi.spyOn(alertService, 'warning');

        (comp as any).startGeneration();

        expect(generationService.generate).not.toHaveBeenCalled();
        expect(attachToJob).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it('refreshes editor content after an accepted generation completes', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const fileSyncService = (comp as any).fileSyncService;
        const beginExpectedUpdateSpy = vi.spyOn(fileSyncService, 'beginExpectedRepositoryUpdate');
        const endExpectedUpdateSpy = vi.spyOn(fileSyncService, 'endExpectedRepositoryUpdate');
        const acceptServerBaseline = vi.fn();
        const unsavedChangesValue = vi.fn().mockReturnValue(false);
        setEditableInstructions(comp, { acceptServerBaseline, unsavedChangesValue });
        const loadSpy = vi
            .spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults')
            .mockReturnValue(of({ body: createMockExercise({ problemStatement: 'Updated problem statement' }) } as any));

        (comp as any).onHyperionGenerationCompleted({
            mode: 'GENERATE',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 },
            completedAt: '2026-07-10T20:00:00Z',
        });

        expect(actions.executeRefresh).toHaveBeenCalledExactlyOnceWith(expect.any(Function));
        expect(beginExpectedUpdateSpy).toHaveBeenCalledExactlyOnceWith(Date.parse('2026-07-10T20:00:00Z'));
        actions.executeRefresh.mock.calls[0][0](true);
        expect(endExpectedUpdateSpy).toHaveBeenCalledOnce();
        expect(loadSpy).toHaveBeenCalledWith(42);
        expect(acceptServerBaseline).toHaveBeenCalledWith(expect.objectContaining({ problemStatement: 'Updated problem statement' }));
        expect(comp.exercise.problemStatement).toBe('Updated problem statement');
    });

    it('keeps editing locked and commit alerts suppressed until both Hyperion refreshes complete', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const reload = new Subject<{ body: ProgrammingExercise }>();
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(reload as any);
        const fileSyncService = (comp as any).fileSyncService;
        const endExpectedUpdateSpy = vi.spyOn(fileSyncService, 'endExpectedRepositoryUpdate');

        (comp as any).onHyperionGenerationCompleted({
            mode: 'GENERATE',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 },
        });

        expect((comp as any).isExerciseGenerationRunning()).toBe(true);
        actions.executeRefresh.mock.calls[0][0](true);
        expect((comp as any).isExerciseGenerationRunning()).toBe(true);
        expect(endExpectedUpdateSpy).not.toHaveBeenCalled();

        reload.next({ body: createMockExercise({ problemStatement: 'Updated problem statement' }) });
        reload.complete();

        expect((comp as any).isExerciseGenerationRunning()).toBe(false);
        expect(endExpectedUpdateSpy).toHaveBeenCalledOnce();
    });

    it('does not apply the exercise reload when the repository refresh fails', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const acceptServerBaseline = vi.fn();
        setEditableInstructions(comp, { acceptServerBaseline, unsavedChangesValue: () => false });
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(
            of({ body: createMockExercise({ problemStatement: 'Mixed server state' }) } as any),
        );
        const endExpectedUpdateSpy = vi.spyOn((comp as any).fileSyncService, 'endExpectedRepositoryUpdate');

        (comp as any).onHyperionGenerationCompleted({ mode: 'GENERATE', liveExerciseChanged: true });
        actions.executeRefresh.mock.calls[0][0](false);

        expect(acceptServerBaseline).not.toHaveBeenCalled();
        expect(comp.exercise.problemStatement).toBe('Implement the specified behavior and cover all required edge cases.');
        expect((comp as any).isExerciseGenerationRunning()).toBe(false);
        expect((comp as any).generationRefreshFailed()).toBe(true);
        expect(endExpectedUpdateSpy).toHaveBeenCalledOnce();
    });

    it('clears the refresh lock and commit suppression when the exercise reload fails', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const reload = new Subject<{ body: ProgrammingExercise }>();
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(reload as any);
        const endExpectedUpdateSpy = vi.spyOn((comp as any).fileSyncService, 'endExpectedRepositoryUpdate');

        (comp as any).onHyperionGenerationCompleted({ mode: 'GENERATE', liveExerciseChanged: true });
        actions.executeRefresh.mock.calls[0][0](true);
        const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        reload.error(new Error('reload failed'));

        expect((comp as any).isExerciseGenerationRunning()).toBe(false);
        expect((comp as any).generationRefreshFailed()).toBe(true);
        expect(endExpectedUpdateSpy).toHaveBeenCalledOnce();
        expect(comp.exercise.problemStatement).toBe('Implement the specified behavior and cover all required edge cases.');
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.editor.errors.refreshFailed', { connectionIssue: '' });

        loadSpy.mockReturnValue(of({ body: createMockExercise({ problemStatement: 'Updated after retry' }) } as any));
        (comp as any).retryHyperionRefresh();
        actions.executeRefresh.mock.calls[1][0](true);

        expect(comp.exercise.problemStatement).toBe('Updated after retry');
        expect((comp as any).generationRefreshFailed()).toBe(false);
    });

    it('does not overwrite problem statement edits made while the Hyperion refresh was in flight', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions, canDeactivate: () => true });
        const reload = new Subject<{ body: ProgrammingExercise }>();
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(reload as any);
        const acceptServerBaseline = vi.fn();
        const unsavedChangesValue = vi.fn().mockReturnValueOnce(false).mockReturnValue(true);
        setEditableInstructions(comp, { acceptServerBaseline, unsavedChangesValue });

        (comp as any).onHyperionGenerationCompleted({
            mode: 'GENERATE',
            verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 },
        });
        reload.next({ body: createMockExercise({ problemStatement: 'Server problem statement' }) });
        reload.complete();
        actions.executeRefresh.mock.calls[0][0](true);

        expect(acceptServerBaseline).not.toHaveBeenCalled();
        expect(comp.exercise.problemStatement).toBe('Implement the specified behavior and cover all required edge cases.');
        expect((comp as any).generationRefreshFailed()).toBe(true);
    });

    it('uses the server revert timestamp as the commit-alert suppression boundary', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const beginExpectedUpdateSpy = vi.spyOn((comp as any).fileSyncService, 'beginExpectedRepositoryUpdate');
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(of({ body: createMockExercise() } as any));

        (comp as any).onHyperionGenerationReverted('2026-07-10T20:01:00Z');

        expect(beginExpectedUpdateSpy).toHaveBeenCalledExactlyOnceWith(Date.parse('2026-07-10T20:01:00Z'));
        expect(actions.executeRefresh).toHaveBeenCalledExactlyOnceWith(expect.any(Function));
    });

    it('does not auto-refresh after Hyperion when the editor has unsaved local changes', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions, canDeactivate: () => false });
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults');
        const alertService = TestBed.inject(AlertService);
        const warningSpy = vi.spyOn(alertService, 'warning');

        (comp as any).onHyperionGenerationCompleted({ mode: 'GENERATE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 } });

        expect(actions.executeRefresh).not.toHaveBeenCalled();
        expect(loadSpy).not.toHaveBeenCalled();
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it('does not auto-refresh after Hyperion when the problem statement has unsaved local changes', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions, canDeactivate: () => true });
        setEditableInstructions(comp, { unsavedChangesValue: () => true });
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults');

        (comp as any).onHyperionGenerationCompleted({ mode: 'GENERATE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 } });

        expect(actions.executeRefresh).not.toHaveBeenCalled();
        expect(loadSpy).not.toHaveBeenCalled();
    });

    it('ignores a stale Hyperion refresh response after navigating to another exercise', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const reload = new Subject<{ body: ProgrammingExercise }>();
        vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults').mockReturnValue(reload as any);

        (comp as any).onHyperionGenerationCompleted({ mode: 'GENERATE', verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 2 } });
        comp.exercise = createMockExercise({ id: 99, problemStatement: 'Current exercise', programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true });
        reload.next({ body: createMockExercise({ id: 42, problemStatement: 'Stale problem statement' }) });
        actions.executeRefresh.mock.calls[0][0](true);

        expect(comp.exercise.problemStatement).toBe('Current exercise');
    });

    it('does not refresh editor content after a rejected generation completes', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const loadSpy = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults');

        (comp as any).onHyperionGenerationCompleted({ mode: 'ADAPT', verdict: { accepted: false, solutionPassed: false, templateFailed: true, testCount: 2 } });

        expect(actions.executeRefresh).not.toHaveBeenCalled();
        expect(loadSpy).not.toHaveBeenCalled();
    });

    it('refreshes editor content after a needs-review generation changed the live exercise', () => {
        const actions = { executeRefresh: vi.fn(), onSave: vi.fn() };
        setCodeEditorContainer(comp, { actions: () => actions });
        const loadSpy = vi
            .spyOn(TestBed.inject(ProgrammingExerciseService), 'findWithTemplateAndSolutionParticipationAndResults')
            .mockReturnValue(of({ body: createMockExercise({ problemStatement: 'Draft problem statement' }) } as any));

        (comp as any).onHyperionGenerationCompleted({
            mode: 'GENERATE',
            completionStatus: 'NEEDS_REVIEW',
            verdict: { accepted: false, solutionPassed: false, templateFailed: true, testCount: 2 },
            liveExerciseChanged: true,
        });

        expect(actions.executeRefresh).toHaveBeenCalledOnce();
        expect(loadSpy).toHaveBeenCalledWith(42);
        actions.executeRefresh.mock.calls[0][0](true);
        expect(comp.exercise.problemStatement).toBe('Draft problem statement');
    });

    it('filters non-consistency, resolved, and outdated review threads out of the adapt count, dialog, and ADAPT payload', () => {
        const resolvedConsistencyThread = { ...consistencyThread(9), resolved: true };
        const outdatedConsistencyThread = { ...consistencyThread(10), outdated: true };
        const activeConsistencyThread = consistencyThread(11);
        reviewCommentService.threads.set([userThread(7), resolvedConsistencyThread, outdatedConsistencyThread, activeConsistencyThread]);
        selectedIds.set([7, 9, 10, 11]);

        expect(comp.selectedAdaptFeedbackCount()).toBe(1);

        (comp as any).openAdaptDialog();

        expect(dialogOpen.mock.calls[0][1].data.findings).toHaveLength(1);
        expect(dialogOpen.mock.calls[0][1].data.findings[0].description).toBe('Fix method signature');
        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: [11],
        });
    });

    it('openAdaptDialog with no selected threads dispatches an ADAPT run with undefined ids', () => {
        (comp as any).openAdaptDialog();

        expect(generationService.generate).toHaveBeenCalledExactlyOnceWith(42, {
            mode: 'ADAPT',
            prompt: 'also rename the method',
            selectedFeedbackThreadIds: undefined,
        });
        expect(attachToJob).toHaveBeenCalledExactlyOnceWith('job-adapt-1', 'ADAPT');
    });

    it('does not dispatch a run when the adapt dialog is dismissed', () => {
        dialogOpen.mockReturnValue({ onClose: of(undefined) });

        (comp as any).openAdaptDialog();

        expect(dialogOpen).toHaveBeenCalledOnce();
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(attachToJob).not.toHaveBeenCalled();
    });

    it('adaptFromThread rolls back a new preview selection when the dialog is dismissed', () => {
        dialogOpen.mockReturnValue({ onClose: of(undefined) });
        reviewCommentService.threads.set([consistencyThread(9)]);

        (comp as any).adaptFromThread(9);

        expect(reviewCommentService.toggleThreadFeedbackSelection).toHaveBeenCalledExactlyOnceWith(9);
        expect(selectedIds()).toEqual([]);
        expect(generationService.generate).not.toHaveBeenCalled();
    });

    it('surfaces an alert and does not attach when starting the ADAPT run fails', () => {
        generationService.generate.mockReturnValue(throwError(() => new Error('boom')));
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');

        (comp as any).openAdaptDialog();

        expect(generationService.generate).toHaveBeenCalledOnce();
        expect(attachToJob).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.hyperion.generationActivity.adaptStartFailed');
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
        expect(warningSpy).toHaveBeenCalledWith('pendingChanges');
    });

    it('does NOT offer or dispatch adapt under Jenkins (localci profile inactive), even with Hyperion enabled', () => {
        const jenkinsProfileService = TestBed.inject(ProfileService);
        vi.spyOn(jenkinsProfileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(jenkinsProfileService, 'isProfileActive').mockReturnValue(false);

        const jenkinsFixture = TestBed.createComponent(CodeEditorInstructorAndEditorContainerComponent);
        const jenkinsComp = jenkinsFixture.componentInstance;
        jenkinsComp.exercise = createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true });
        const jenkinsAttach = vi.fn();
        (jenkinsComp as any).generationActivity = () => ({ attachToJob: jenkinsAttach });

        expect((jenkinsComp as any).hyperionEnabled).toBe(true);
        expect((jenkinsComp as any).hyperionGenerationSupported).toBe(false);
        expect((jenkinsComp as any).canAdaptWithFeedback()).toBe(false);
        expect((jenkinsComp as any).showGenerationActivity()).toBe(false);

        (jenkinsComp as any).openAdaptDialog();
        expect(generationService.generate).not.toHaveBeenCalled();
        expect(jenkinsAttach).not.toHaveBeenCalled();
        jenkinsFixture.destroy();
    });

    it('keeps retained activity visible but does not offer new generation after release or participation', () => {
        const localCiProfileService = TestBed.inject(ProfileService);
        vi.spyOn(localCiProfileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(localCiProfileService, 'isProfileActive').mockReturnValue(true);

        comp.exercise = createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, releaseDate: dayjs().subtract(1, 'minute') });
        expect((comp as any).showGenerationActivity()).toBe(true);
        expect((comp as any).canGenerateExercise()).toBe(false);

        comp.exercise = createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, releaseDate: undefined });
        expect((comp as any).showGenerationActivity()).toBe(true);
        expect((comp as any).canGenerateExercise()).toBe(false);

        comp.exercise = createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, studentParticipations: [{} as any] });
        expect((comp as any).showGenerationActivity()).toBe(true);
        expect((comp as any).canGenerateExercise()).toBe(false);

        comp.exercise = createMockExercise({ programmingLanguage: ProgrammingLanguage.JAVA, isAtLeastEditor: true, numberOfParticipations: 1 });
        expect((comp as any).showGenerationActivity()).toBe(true);
        expect((comp as any).canGenerateExercise()).toBe(false);
    });
});
