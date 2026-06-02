import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, Subject, of, throwError } from 'rxjs';
import * as Y from 'yjs';
import { DebugElement, Signal, signal } from '@angular/core';

// Mock y-monaco to avoid needing full Monaco API in tests.
vi.mock('y-monaco', () => ({
    // Use a real `function` (not an arrow) so the production code can invoke it with `new`.
    MonacoBinding: vi.fn(function (this: any) {
        this.destroy = vi.fn();
    }),
}));
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { ResultService } from 'app/exercise/result/result.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { IProgrammingExerciseGradingService, ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { RewriteAction } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProblemStatementSyncService, ProblemStatementSyncState } from 'app/exercise/synchronization/services/problem-statement-sync.service';
import { editor } from 'test/helpers/mocks/mock-monaco-editor';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { MonacoBinding } from 'y-monaco';

/**
 * Typed view onto the protected/private internals the spec needs to read or override without a
 * blanket `(component as any)` cast. `markdownEditorMonaco` is a `viewChild()` signal on the real
 * component; the helpers below let the spec stub it (the child is mocked, so the real query never
 * resolves to a usable Monaco editor).
 */
type EditableInstructionInternalsOverrides = {
    markdownEditorMonaco: Signal<MarkdownEditorMonacoComponent | undefined>;
    problemStatementSyncState?: ProblemStatementSyncState;
    problemStatementBinding?: MonacoBinding;
};
type EditableInstructionInternals = Omit<ProgrammingExerciseEditableInstructionComponent, keyof EditableInstructionInternalsOverrides> & EditableInstructionInternalsOverrides;
const internals = (c: ProgrammingExerciseEditableInstructionComponent): EditableInstructionInternals => c as unknown as EditableInstructionInternals;
/** Override the `markdownEditorMonaco` viewChild signal with a fixed stub value. */
const setMarkdownEditorMonaco = (c: ProgrammingExerciseEditableInstructionComponent, value: MarkdownEditorMonacoComponent | undefined): void => {
    internals(c).markdownEditorMonaco = (() => value) as Signal<MarkdownEditorMonacoComponent | undefined>;
};

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let gradingService: IProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let alertService: AlertService;
    let exerciseReviewCommentService: ExerciseReviewCommentService;

    let subscribeForTestCaseSpy: ReturnType<typeof vi.spyOn>;
    let getLatestResultWithFeedbacksStub: ReturnType<typeof vi.spyOn>;
    let generateHtmlSubjectStub: ReturnType<typeof vi.spyOn>;

    const templateParticipation = new TemplateProgrammingExerciseParticipation();
    templateParticipation.id = 99;

    const exercise = { id: 30, templateParticipation } as ProgrammingExercise;
    const testCases = [
        { testName: 'test1', active: true },
        { testName: 'test2', active: true },
        { testName: 'test3', active: false },
    ];

    const mockProfileInfo = { activeModuleFeatures: [MODULE_FEATURE_HYPERION] } as ProfileInfo;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '1', exerciseId: 1 }) },
        url: {
            pipe: () => ({
                subscribe: () => {},
            }),
        },
    } as ActivatedRoute;

    const yDoc = new Y.Doc();
    const yText = yDoc.getText('problem-statement');
    const yAwareness = {} as any;
    const stateReplaced$ = new Subject<{ doc: Y.Doc; text: Y.Text; awareness: any }>();
    const initialSyncFinalized$ = new Subject<{ contentChangedDuringFinalize: boolean; contentDivergedFromFallback: boolean; finalContent: string }>();
    const problemStatementSyncServiceMock = {
        init: vi.fn().mockReturnValue({ doc: yDoc, text: yText, awareness: yAwareness }),
        reset: vi.fn(),
        stateReplaced$: stateReplaced$.asObservable(),
        initialSyncFinalized$: initialSyncFinalized$.asObservable(),
        isAwaitingInitialSync: vi.fn(() => false),
    };

    const defaultForceRender$ = new Subject<void>();
    const setRequiredInputs = (
        fixtureRef: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>,
        exerciseInput: ProgrammingExercise = exercise,
        forceRender$: Observable<void> = defaultForceRender$,
    ) => {
        fixtureRef.componentRef.setInput('exercise', exerciseInput);
        fixtureRef.componentRef.setInput('initialEditorHeight', 'external');
        fixtureRef.componentRef.setInput('forceRender', forceRender$);
    };

    beforeEach(() => {
        return (
            TestBed.configureTestingModule({
                providers: [
                    { provide: ResultService, useClass: MockResultService },
                    { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                    { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: AlertService, useClass: MockAlertService },
                    { provide: ProblemStatementSyncService, useValue: problemStatementSyncServiceMock },
                    MockProvider(ExerciseReviewCommentService, { threads: signal([]), markInlineFixAppliedInContext: vi.fn() }),
                    { provide: ActivatedRoute, useValue: route },
                    MockProvider(ProfileService, {
                        getProfileInfo: () => mockProfileInfo,
                        isProfileActive: vi.fn().mockReturnValue(false),
                        isModuleFeatureActive: vi.fn().mockReturnValue(true),
                    }),
                    MockProvider(ProgrammingExerciseParticipationService, {
                        getLatestResultWithFeedback: vi.fn(),
                    }),
                    { provide: AccountService, useClass: MockAccountService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            })
                // Swap the standalone children imported by the component-under-test for mocks so the
                // template renders without instantiating the real Monaco editor / instruction renderer.
                .overrideComponent(ProgrammingExerciseEditableInstructionComponent, {
                    remove: {
                        imports: [
                            MarkdownEditorMonacoComponent,
                            ProgrammingExerciseInstructionAnalysisComponent,
                            ProgrammingExerciseInstructionComponent,
                            NgbTooltip,
                            ArtemisTranslatePipe,
                        ],
                    },
                    add: {
                        imports: [
                            MockComponent(MarkdownEditorMonacoComponent),
                            MockComponent(ProgrammingExerciseInstructionAnalysisComponent),
                            MockComponent(ProgrammingExerciseInstructionComponent),
                            MockDirective(NgbTooltip),
                            MockPipe(ArtemisTranslatePipe),
                        ],
                    },
                })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                    comp = fixture.componentInstance;
                    debugElement = fixture.debugElement;
                    gradingService = TestBed.inject(ProgrammingExerciseGradingService);
                    (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
                    programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
                    exerciseReviewCommentService = TestBed.inject(ExerciseReviewCommentService);
                    subscribeForTestCaseSpy = vi.spyOn(gradingService, 'subscribeForTestCases');
                    getLatestResultWithFeedbacksStub = vi.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                    generateHtmlSubjectStub = vi.spyOn(comp.generateHtmlSubject, 'next');
                    programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                    alertService = TestBed.inject(AlertService);
                    setRequiredInputs(fixture, { id: undefined } as ProgrammingExercise);
                })
        );
    });

    afterEach(() => {
        (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    it('should initialize sync service', () => {
        const exercise = { id: 30, templateParticipation, problemStatement: 'test' } as ProgrammingExercise;
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        // Set up mock for markdownEditorMonaco using the mock-monaco-editor helper
        const mockEditor = editor.create();
        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getModel: () => mockEditor.getModel(),
                getEditor: () => mockEditor,
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        // Trigger ngAfterViewInit manually since the ViewChild is now set
        comp.ngAfterViewInit();

        expect(problemStatementSyncServiceMock.init).toHaveBeenCalledWith(exercise.id, exercise.problemStatement);

        fixture.destroy();
    });

    it('skips initializing sync when edit mode disabled', () => {
        fixture.componentRef.setInput('editMode', false);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'content' });

        fixture.detectChanges();

        expect(problemStatementSyncServiceMock.init).not.toHaveBeenCalled();
    });

    it('emits unsaved flag on user edits', () => {
        const hasUnsavedSpy = vi.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'old' });
        fixture.detectChanges();

        comp.updateProblemStatement('changed');

        expect(hasUnsavedSpy).toHaveBeenCalledWith(true);
    });

    it('does not emit unsaved flag during initial sync bootstrap', () => {
        const hasUnsavedSpy = vi.fn();
        const instructionChangeSpy = vi.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);
        comp.instructionChange.subscribe(instructionChangeSpy);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'old' });
        fixture.detectChanges();

        internals(comp).problemStatementSyncState = { doc: yDoc, text: yText, awareness: yAwareness } as ProblemStatementSyncState;
        problemStatementSyncServiceMock.isAwaitingInitialSync.mockReturnValue(true);

        comp.updateProblemStatement('changed-during-sync');

        expect(hasUnsavedSpy).not.toHaveBeenCalled();
        expect(comp.unsavedChangesValue).toBe(false);
        expect(instructionChangeSpy).toHaveBeenCalledWith('changed-during-sync');
    });

    it('does not emit unsaved flag for finalize hydration when a bootstrap change was suppressed', () => {
        const hasUnsavedSpy = vi.fn();
        const instructionChangeSpy = vi.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);
        comp.instructionChange.subscribe(instructionChangeSpy);

        const exerciseWithStatement = { ...exercise, problemStatement: 'old' } as ProgrammingExercise;
        setRequiredInputs(fixture, exerciseWithStatement);
        fixture.detectChanges();

        const mockEditor = editor.create();
        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getModel: () => mockEditor.getModel(),
                getEditor: () => mockEditor,
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        comp.ngAfterViewInit();

        problemStatementSyncServiceMock.isAwaitingInitialSync.mockReturnValue(true);
        comp.updateProblemStatement('changed-during-sync');
        expect(hasUnsavedSpy).not.toHaveBeenCalled();

        problemStatementSyncServiceMock.isAwaitingInitialSync.mockReturnValue(false);
        initialSyncFinalized$.next({ contentChangedDuringFinalize: true, contentDivergedFromFallback: false, finalContent: 'changed-after-finalize' });
        comp.updateProblemStatement('changed-after-finalize');

        expect(hasUnsavedSpy).not.toHaveBeenCalled();
        expect(comp.unsavedChangesValue).toBe(false);
        expect(instructionChangeSpy).toHaveBeenCalledWith('changed-after-finalize');
    });

    it('does not suppress first user edit after finalize when no bootstrap change was suppressed', () => {
        const hasUnsavedSpy = vi.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);

        const exerciseWithStatement = { ...exercise, problemStatement: 'old' } as ProgrammingExercise;
        setRequiredInputs(fixture, exerciseWithStatement);
        fixture.detectChanges();

        const mockEditor = editor.create();
        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getModel: () => mockEditor.getModel(),
                getEditor: () => mockEditor,
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        comp.ngAfterViewInit();

        initialSyncFinalized$.next({ contentChangedDuringFinalize: true, contentDivergedFromFallback: false, finalContent: 'first-user-edit-after-finalize' });
        comp.updateProblemStatement('first-user-edit-after-finalize');

        expect(hasUnsavedSpy).toHaveBeenCalledWith(true);
        expect(comp.unsavedChangesValue).toBe(true);
    });

    it('marks unsaved when finalized sync content diverges from fallback', () => {
        const hasUnsavedSpy = vi.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);

        const exerciseWithStatement = { ...exercise, problemStatement: 'saved-server-content' } as ProgrammingExercise;
        setRequiredInputs(fixture, exerciseWithStatement);
        fixture.detectChanges();

        const mockEditor = editor.create();
        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getModel: () => mockEditor.getModel(),
                getEditor: () => mockEditor,
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        comp.ngAfterViewInit();

        initialSyncFinalized$.next({
            contentChangedDuringFinalize: true,
            contentDivergedFromFallback: true,
            finalContent: 'remote-unsaved-content',
        });

        expect(hasUnsavedSpy).toHaveBeenCalledWith(true);
        expect(comp.unsavedChangesValue).toBe(true);
    });

    it('should reset sync service on component destroy', () => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        fixture.destroy();

        expect(problemStatementSyncServiceMock.reset).toHaveBeenCalled();
    });

    it('should not have any test cases if the test case service emits an empty array', () => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);
        expect(comp.exerciseTestCases).toHaveLength(0);

        fixture.destroy();
    });

    it('should have test cases according to the result of the test case service if it does not return an empty array', () => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);
        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);
        const testCaseValues = comp.testCaseAction.getValues();
        expect(testCaseValues).toHaveLength(2);
        expect(testCaseValues).toEqual([
            { value: 'test1', id: 'test1' },
            { value: 'test2', id: 'test2' },
        ]);

        fixture.destroy();
    });

    it('should update test cases if a new test case result comes in', () => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases([{ testName: 'testX' }]);

        expect(comp.exerciseTestCases).toHaveLength(0);

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);

        fixture.destroy();
    });

    it('should try to retrieve the test case values from the solution repos last build result if there are no testCases (empty result)', () => {
        const subject = new Subject<Result>();
        getLatestResultWithFeedbacksStub.mockReturnValue(subject);

        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        // No test cases available, might be that the solution build never ran to create tests...
        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(undefined);

        fixture.detectChanges();

        expect(comp.exerciseTestCases).toHaveLength(0);
        expect(getLatestResultWithFeedbacksStub).toHaveBeenNthCalledWith(1, exercise.templateParticipation!.id!);

        subject.next({ feedbacks: [{ testCase: { testName: 'testY' } }, { testCase: { testName: 'testX' } }] } as Result);

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['testX', 'testY']);

        fixture.destroy();
    });

    it('should not try to query test cases or solution participation results if the exercise is being created (there can be no test cases yet)', () => {
        fixture.componentRef.setInput('editMode', false);
        const newExercise = { ...exercise, id: undefined };

        setRequiredInputs(fixture, newExercise as ProgrammingExercise);

        fixture.detectChanges();

        expect(comp.exerciseTestCases).toHaveLength(0);

        expect(comp.testCaseSubscription).toBeUndefined();
        expect(subscribeForTestCaseSpy).not.toHaveBeenCalled();
        expect(getLatestResultWithFeedbacksStub).not.toHaveBeenCalled();

        const saveProblemStatementButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveProblemStatementButton).toBeNull();

        fixture.destroy();
    });

    it('should re-render the preview html when forceRender has emitted', () => {
        const forceRenderSubject = new Subject<void>();
        setRequiredInputs(fixture, exercise, forceRenderSubject.asObservable());

        fixture.detectChanges();

        // Initial render is triggered in ngAfterViewInit when showPreview is true
        const callCountAfterInit = generateHtmlSubjectStub.mock.calls.length;

        forceRenderSubject.next();

        // forceRender should trigger an additional call
        expect(generateHtmlSubjectStub).toHaveBeenCalledTimes(callCountAfterInit + 1);

        fixture.destroy();
    });

    it('should update the code editor annotations when receiving a new ProblemStatementAnalysis', () => {
        const setAnnotationsStub = vi.fn();
        // The component is mocked, so we need to set the monacoEditor property to a mock object.
        setMarkdownEditorMonaco(comp, { monacoEditor: () => ({ setAnnotations: setAnnotationsStub }) } as unknown as MarkdownEditorMonacoComponent);

        const analysis = new Map();
        analysis.set(0, { lineNumber: 0, invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] });
        analysis.set(2, {
            lineNumber: 2,
            invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
        });

        const expectedWarnings = [
            { column: 0, row: 0, text: ' - artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase', type: 'warning' },
            { column: 0, row: 2, text: ' - artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase', type: 'warning' },
        ];

        comp.onAnalysisUpdate(analysis);

        expect(setAnnotationsStub).toHaveBeenCalledExactlyOnceWith(expectedWarnings);

        fixture.destroy();
    });

    it('should save the problem statement to the server', () => {
        fixture.componentRef.setInput('editMode', true);
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        const updateProblemStatement = vi.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(of(new HttpResponse({ body: exercise })));
        const problemStatementSavedSpy = vi.spyOn(comp.onProblemStatementSaved, 'emit');

        comp.updateProblemStatement('new problem statement');
        fixture.componentRef.setInput('exercise', { ...exercise, problemStatement: 'new problem statement' } as ProgrammingExercise);
        fixture.detectChanges();
        comp.saveInstructions({ stopPropagation: () => {} } as Event);

        expect(updateProblemStatement).toHaveBeenCalledExactlyOnceWith(exercise.id, 'new problem statement');
        expect(problemStatementSavedSpy).toHaveBeenCalledOnce();
    });

    it('should prefer current editor content when saving the problem statement', () => {
        fixture.componentRef.setInput('editMode', true);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'stale input value' } as ProgrammingExercise);
        fixture.detectChanges();

        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getText: vi.fn().mockReturnValue('live editor value'),
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        const updateProblemStatement = vi.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(of(new HttpResponse({ body: exercise })));

        comp.saveInstructions({ stopPropagation: () => {} } as Event);

        expect(updateProblemStatement).toHaveBeenCalledExactlyOnceWith(exercise.id, 'live editor value');
    });

    it('should log an error on save', () => {
        const updateProblemStatementSpy = vi.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(throwError(() => undefined));
        const logErrorSpy = vi.spyOn(alertService, 'error');
        const problemStatementSavedSpy = vi.spyOn(comp.onProblemStatementSaved, 'emit');

        fixture.componentRef.setInput('editMode', true);
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        comp.saveInstructions(new KeyboardEvent('cmd+s'));
        expect(updateProblemStatementSpy).toHaveBeenCalledOnce();
        expect(logErrorSpy).toHaveBeenCalledOnce();
        expect(problemStatementSavedSpy).not.toHaveBeenCalled();
        expect(comp.savingInstructions).toBe(false);
    });

    it('should save on key commands', () => {
        const saveInstructionsSpy = vi.spyOn(comp, 'saveInstructions');
        fixture.componentRef.setInput('editMode', true);
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        comp.saveOnControlAndS(new KeyboardEvent('ctrl+s'));
        expect(saveInstructionsSpy).toHaveBeenCalledOnce();

        comp.saveOnCommandAndS(new KeyboardEvent('cmd+s'));
        expect(saveInstructionsSpy).toHaveBeenCalledOnce();
    });

    it('should have intelligence actions when Hyperion is active', () => {
        setRequiredInputs(fixture, { ...exercise, course: { id: 1 } as any } as ProgrammingExercise);
        comp.hyperionEnabled = true;
        fixture.detectChanges();

        const actions = comp.artemisIntelligenceActions();
        expect(actions).toHaveLength(1);
        expect(actions[0]).toBeInstanceOf(RewriteAction);
    });

    it('should cleanup subscriptions on destroy', () => {
        setRequiredInputs(fixture, exercise);
        fixture.componentRef.setInput('participation', templateParticipation);
        fixture.detectChanges();

        // Get subscription reference before destroy
        const testCaseSubscription = comp.testCaseSubscription;

        // Destroy the component
        comp.ngOnDestroy();

        // Verify cleanup occurred
        if (testCaseSubscription) {
            expect(testCaseSubscription.closed).toBe(true);
        }
    });

    it('should subscribe for test cases when exercise changes', () => {
        const newExercise = { ...exercise, id: 31 } as ProgrammingExercise;
        setRequiredInputs(fixture, exercise);
        fixture.componentRef.setInput('participation', templateParticipation);
        fixture.detectChanges();

        // Reset spy
        generateHtmlSubjectStub.mockClear();

        // Trigger exercise change
        fixture.componentRef.setInput('exercise', newExercise);
        fixture.detectChanges();

        expect(subscribeForTestCaseSpy).toHaveBeenCalledWith(newExercise.id);

        fixture.destroy();
    });

    it('should update inline refinement position with signal on selection change', () => {
        const selection = {
            startLine: 1,
            endLine: 2,
            startColumn: 5,
            endColumn: 10,
            selectedText: 'Some selected text',
            screenPosition: { top: 100, left: 200 },
        };

        // Enable hyperion
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(true);
        fixture.destroy();
        fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
        comp = fixture.componentInstance;

        // Mock container bounding rect to verify viewport-to-container coordinate conversion
        vi.spyOn(fixture.nativeElement, 'getBoundingClientRect').mockReturnValue({ top: 30, left: 50 } as DOMRect);

        comp.onEditorSelectionChange(selection);

        // Position uses viewport-relative coordinates with clamping
        expect(comp.inlineRefinementPosition()).toEqual({ top: 100, left: 200 });
        expect(comp.selectedTextForRefinement()).toBe('Some selected text');
        expect(comp.selectionPositionInfo()).toEqual({
            startLine: 1,
            endLine: 2,
            startColumn: 5,
            endColumn: 10,
        });
    });

    it('should hide inline refinement button when selection is empty', () => {
        comp.inlineRefinementPosition.set({ top: 100, left: 200 });
        comp.selectedTextForRefinement.set('some text');
        comp.selectionPositionInfo.set({ startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        comp.onEditorSelectionChange(undefined);

        expect(comp.inlineRefinementPosition()).toBeUndefined();
        expect(comp.selectedTextForRefinement()).toBe('');
        expect(comp.selectionPositionInfo()).toBeUndefined();
    });

    it('should hide inline refinement button when selection has only whitespace', () => {
        vi.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(true);
        fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
        comp = fixture.componentInstance;

        const selection = {
            startLine: 1,
            endLine: 1,
            startColumn: 0,
            endColumn: 5,
            selectedText: '   ',
            screenPosition: { top: 100, left: 200 },
        };

        comp.onEditorSelectionChange(selection);

        expect(comp.inlineRefinementPosition()).toBeUndefined();
    });

    it('should emit inline refinement event and hide button on refine', () => {
        comp.inlineRefinementPosition.set({ top: 100, left: 200 });
        comp.selectedTextForRefinement.set('some text');
        comp.selectionPositionInfo.set({ startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        const emitSpy = vi.spyOn(comp.onInlineRefinement, 'emit');

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 0,
            endColumn: 10,
        };

        comp.onInlineRefine(event);

        expect(emitSpy).toHaveBeenCalledWith(event);
        expect(comp.inlineRefinementPosition()).toBeUndefined();
        expect(comp.selectedTextForRefinement()).toBe('');
        expect(comp.selectionPositionInfo()).toBeUndefined();
    });

    it('should persist problem statement and mark inline fix as applied', () => {
        fixture.componentRef.setInput('editMode', true);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'stale input value' } as ProgrammingExercise);
        fixture.detectChanges();

        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getText: vi.fn().mockReturnValue('live editor value'),
            }),
        } as unknown as MarkdownEditorMonacoComponent);
        (exerciseReviewCommentService.threads as any).set([
            {
                id: 10,
                comments: [
                    {
                        id: 20,
                        type: CommentType.CONSISTENCY_CHECK,
                        createdDate: '2024-01-01T00:00:00Z',
                        content: {
                            contentType: CommentContentType.CONSISTENCY_CHECK,
                            text: 'issue',
                        },
                    },
                ],
            },
        ]);
        const updateProblemStatement = vi.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(of(new HttpResponse({ body: exercise })));

        comp.onApplyInlineFix({ threadId: 10 });

        expect(updateProblemStatement).toHaveBeenCalledExactlyOnceWith(exercise.id, 'live editor value');
        expect(exerciseReviewCommentService.markInlineFixAppliedInContext).toHaveBeenCalledExactlyOnceWith(20);
    });

    it('should ignore inline fix application while instructions are already saving', () => {
        comp.savingInstructions = true;

        comp.onApplyInlineFix({ threadId: 10 });

        expect(exerciseReviewCommentService.markInlineFixAppliedInContext).not.toHaveBeenCalled();
    });

    it('should delegate diff-mode refinement actions to the markdown editor', () => {
        const updateProblemStatementSpy = vi.spyOn(comp, 'updateProblemStatement');
        const applyRefinedContent = vi.fn();
        const revertAll = vi.fn();
        setMarkdownEditorMonaco(comp, {
            applyRefinedContent,
            revertAll,
            monacoEditor: () => ({
                getText: vi.fn().mockReturnValue('reverted content'),
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        comp.applyRefinedContent('refined content');
        comp.revertAll();

        expect(applyRefinedContent).toHaveBeenCalledExactlyOnceWith('refined content');
        expect(revertAll).toHaveBeenCalledOnce();
        expect(updateProblemStatementSpy).toHaveBeenCalledExactlyOnceWith('reverted content');
    });

    it('should get current content from editor', () => {
        const mockGetText = vi.fn().mockReturnValue('editor content');
        setMarkdownEditorMonaco(comp, {
            monacoEditor: () => ({
                getText: mockGetText,
            }),
        } as unknown as MarkdownEditorMonacoComponent);

        const content = comp.getCurrentContent();

        expect(content).toBe('editor content');
        expect(mockGetText).toHaveBeenCalled();
    });

    it('should return undefined when editor is not available for getCurrentContent', () => {
        setMarkdownEditorMonaco(comp, undefined);

        const content = comp.getCurrentContent();

        expect(content).toBeUndefined();
    });

    it('should return undefined when monacoEditor is not available for getCurrentContent', () => {
        setMarkdownEditorMonaco(comp, { monacoEditor: () => undefined } as unknown as MarkdownEditorMonacoComponent);

        const content = comp.getCurrentContent();

        expect(content).toBeUndefined();
    });

    it('should hide inline refinement button explicitly', () => {
        comp.inlineRefinementPosition.set({ top: 100, left: 200 });
        comp.selectedTextForRefinement.set('text');
        comp.selectionPositionInfo.set({ startLine: 1, endLine: 1, startColumn: 0, endColumn: 5 });

        comp.hideInlineRefinementButton();

        expect(comp.inlineRefinementPosition()).toBeUndefined();
        expect(comp.selectedTextForRefinement()).toBe('');
        expect(comp.selectionPositionInfo()).toBeUndefined();
    });

    /**
     * CRLF normalization tests for problem statement sync.
     *
     * Background: Yjs (CRDT) and Monaco use flat character offsets for insert/delete
     * operations. If one peer's Monaco model uses CRLF (2 chars per newline) while
     * another uses LF (1 char), their offsets diverge by 1 per line break, causing
     * all collaborative edits after the first newline to land at wrong positions.
     *
     * The code editor path already normalizes CRLF → LF and enforces LF EOL on the
     * Monaco model at every binding point. These tests verify the problem statement
     * path now does the same:
     *
     * 1. Initial seed: CRLF in the exercise's problemStatement is stripped before
     *    passing to ProblemStatementSyncService.init(), preventing CRLF from entering
     *    the Yjs document.
     *
     * 2. Model preparation: The Monaco model is cleared (setValue('')) and its EOL is
     *    set to LF before creating the MonacoBinding, ensuring the binding starts
     *    from a known-clean state.
     *
     * 3. Late leader replacement (stateReplaced$): When a new leader sends its full
     *    Yjs state, the replacement text is normalized and EOL is re-enforced after
     *    setValue(), because Monaco's setValue() resets EOL detection based on the
     *    content it receives.
     */
    describe('CRLF normalization for problem statement sync', () => {
        const setupSyncTest = () => {
            // getModel() is typed `MockTextModel | null` (matching Monaco); a freshly created editor always has a model.
            const mockModel = editor.create().getModel()!;
            const setValueSpy = vi.spyOn(mockModel, 'setValue');
            const setEOLSpy = vi.spyOn(mockModel, 'setEOL');
            const mockEditorInstance = editor.create();
            vi.spyOn(mockEditorInstance, 'getModel').mockReturnValue(mockModel);

            setMarkdownEditorMonaco(comp, {
                monacoEditor: () => ({
                    getModel: () => mockModel,
                    getEditor: () => mockEditorInstance,
                }),
            } as unknown as MarkdownEditorMonacoComponent);

            return { mockModel, setValueSpy, setEOLSpy };
        };

        it('should normalize CRLF to LF in initial problem statement before passing to sync service', () => {
            const crlfContent = 'line1\r\nline2\r\nline3';
            const exerciseWithCrlf = { id: 30, templateParticipation, problemStatement: crlfContent } as ProgrammingExercise;
            setRequiredInputs(fixture, exerciseWithCrlf);
            fixture.detectChanges();

            setupSyncTest();
            comp.ngAfterViewInit();

            // init() must receive LF-only content so the Yjs document is never seeded with CRLF
            expect(problemStatementSyncServiceMock.init).toHaveBeenCalledWith(exerciseWithCrlf.id, 'line1\nline2\nline3');

            fixture.destroy();
        });

        it('should clear model and enforce LF EOL before creating the binding', () => {
            const exerciseWithStatement = { id: 30, templateParticipation, problemStatement: 'content' } as ProgrammingExercise;
            setRequiredInputs(fixture, exerciseWithStatement);
            fixture.detectChanges();

            const { setValueSpy, setEOLSpy } = setupSyncTest();
            comp.ngAfterViewInit();

            // Model must be cleared before binding so MonacoBinding starts from empty state
            expect(setValueSpy).toHaveBeenCalledWith('');
            // EOL must be enforced to LF so Monaco's offset math matches Yjs
            expect(setEOLSpy).toHaveBeenCalledWith(editor.EndOfLineSequence.LF);

            fixture.destroy();
        });

        it('should normalize CRLF and re-enforce LF EOL on late leader replacement via stateReplaced$', () => {
            const exerciseWithStatement = { id: 30, templateParticipation, problemStatement: 'initial' } as ProgrammingExercise;
            setRequiredInputs(fixture, exerciseWithStatement);
            fixture.detectChanges();

            const { setValueSpy, setEOLSpy } = setupSyncTest();
            comp.ngAfterViewInit();

            // Clear spies from initial setup to isolate stateReplaced$ assertions
            setValueSpy.mockClear();
            setEOLSpy.mockClear();

            // Simulate a late leader sending state with CRLF content.
            // This can happen when a Windows peer seeded the Yjs doc with CRLF.
            const replacementDoc = new Y.Doc();
            const replacementText = replacementDoc.getText('problem-statement');
            replacementText.insert(0, 'replaced\r\nwith\r\ncrlf');
            const replacementAwareness = {} as any;

            stateReplaced$.next({ doc: replacementDoc, text: replacementText, awareness: replacementAwareness });

            // setValue must receive normalized (LF-only) content
            expect(setValueSpy).toHaveBeenCalledWith('replaced\nwith\ncrlf');
            // EOL must be re-enforced after setValue because setValue resets Monaco's EOL detection
            expect(setEOLSpy).toHaveBeenCalledWith(editor.EndOfLineSequence.LF);

            fixture.destroy();
        });

        it('should destroy old binding before calling setValue during state replacement', () => {
            const exerciseWithStatement = { id: 30, templateParticipation, problemStatement: 'initial' } as ProgrammingExercise;
            setRequiredInputs(fixture, exerciseWithStatement);
            fixture.detectChanges();

            const { setValueSpy } = setupSyncTest();
            comp.ngAfterViewInit();

            setValueSpy.mockClear();

            // Track call order: the binding's destroy wrapper must fire before setValue.
            // createProblemStatementBinding wraps the mock's destroy with a guard, so we
            // intercept the wrapper directly on the live binding reference.
            const callOrder: string[] = [];
            const currentBinding = internals(comp).problemStatementBinding!;
            const originalWrappedDestroy = currentBinding.destroy;
            currentBinding.destroy = () => {
                callOrder.push('destroy');
                originalWrappedDestroy();
            };
            setValueSpy.mockImplementation(() => callOrder.push('setValue'));

            const replacementDoc = new Y.Doc();
            const replacementText = replacementDoc.getText('problem-statement');
            replacementText.insert(0, 'new content');
            const replacementAwareness = {} as any;

            stateReplaced$.next({ doc: replacementDoc, text: replacementText, awareness: replacementAwareness });

            // Old binding must be detached before mutating the model to prevent
            // spurious delete+insert propagation through the stale Y.Doc to peers.
            expect(callOrder).toEqual(['destroy', 'setValue']);

            fixture.destroy();
        });
    });
});
