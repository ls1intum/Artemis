import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DebugElement } from '@angular/core';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
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
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let gradingService: IProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let alertService: AlertService;

    let subscribeForTestCaseSpy: jest.SpyInstance;
    let getLatestResultWithFeedbacksStub: jest.SpyInstance;
    let generateHtmlSubjectStub: jest.SpyInstance;

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

    const problemStatementUpdates$ = new Subject<string>();
    const problemStatementSyncServiceMock = {
        init: jest.fn().mockReturnValue(problemStatementUpdates$.asObservable()),
        queueLocalChange: jest.fn(),
        reset: jest.fn(),
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
        return TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FaIconComponent],
            declarations: [
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionAnalysisComponent),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: ProblemStatementSyncService, useValue: problemStatementSyncServiceMock },
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ProfileService, {
                    getProfileInfo: () => mockProfileInfo,
                    isProfileActive: jest.fn().mockReturnValue(false),
                    isModuleFeatureActive: jest.fn().mockReturnValue(true),
                }),
                MockProvider(ProgrammingExerciseParticipationService, {
                    getLatestResultWithFeedback: jest.fn(),
                }),
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                gradingService = TestBed.inject(ProgrammingExerciseGradingService);
                (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
                programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
                subscribeForTestCaseSpy = jest.spyOn(gradingService, 'subscribeForTestCases');
                getLatestResultWithFeedbacksStub = jest.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                generateHtmlSubjectStub = jest.spyOn(comp.generateHtmlSubject, 'next');
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                alertService = TestBed.inject(AlertService);
                setRequiredInputs(fixture, { id: undefined } as ProgrammingExercise);
            });
    });

    afterEach(() => {
        (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
        jest.clearAllMocks();
        jest.restoreAllMocks();
    });

    it('should initialize sync service and subscribe to remote updates', fakeAsync(() => {
        const exercise = { id: 30, templateParticipation, problemStatement: 'test' } as ProgrammingExercise;
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();
        tick();

        expect(problemStatementSyncServiceMock.init).toHaveBeenCalledWith(exercise.id, exercise.problemStatement);

        // Simulate remote update
        problemStatementUpdates$.next('remote problem statement');
        tick();

        // Verify component applied the update without queueing it
        expect(comp.exercise().problemStatement).toBe('remote problem statement');
        expect(problemStatementSyncServiceMock.queueLocalChange).not.toHaveBeenCalled();

        fixture.destroy();
        flush();
    }));

    it('skips initializing sync when edit mode disabled', fakeAsync(() => {
        comp.editMode = false;
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'content' });

        fixture.detectChanges();
        tick();

        expect(problemStatementSyncServiceMock.init).not.toHaveBeenCalled();
    }));

    it('queues local changes and emits unsaved flag on user edits', () => {
        const hasUnsavedSpy = jest.fn();
        comp.hasUnsavedChanges.subscribe(hasUnsavedSpy);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'old' });
        fixture.detectChanges();

        comp.updateProblemStatement('changed');

        expect(problemStatementSyncServiceMock.queueLocalChange).toHaveBeenCalledWith('changed');
        expect(hasUnsavedSpy).toHaveBeenCalledWith(true);
    });

    it('applies remote updates and marks unsaved state', () => {
        const instructionSpy = jest.fn();
        comp.instructionChange.subscribe(instructionSpy);
        setRequiredInputs(fixture, { ...exercise, problemStatement: 'old' });

        (comp as any).applyRemoteProblemStatementUpdate('remote content');

        expect(comp.exercise().problemStatement).toBe('remote content');
        expect(instructionSpy).toHaveBeenCalledWith('remote content');
        expect(comp.unsavedChangesValue).toBeTrue();
    });

    it('should reset sync service on component destroy', () => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        fixture.destroy();

        expect(problemStatementSyncServiceMock.reset).toHaveBeenCalled();
    });

    it('should not have any test cases if the test case service emits an empty array', fakeAsync(() => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);
        expect(comp.exerciseTestCases).toHaveLength(0);

        fixture.destroy();
        flush();
    }));

    it('should have test cases according to the result of the test case service if it does not return an empty array', fakeAsync(() => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);
        tick();

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
        flush();
    }));

    it('should update test cases if a new test case result comes in', fakeAsync(() => {
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);
        tick();

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases([{ testName: 'testX' }]);
        tick();

        expect(comp.exerciseTestCases).toHaveLength(0);

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);

        fixture.destroy();
        flush();
    }));

    it('should try to retrieve the test case values from the solution repos last build result if there are no testCases (empty result)', fakeAsync(() => {
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
        tick();

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['testX', 'testY']);

        fixture.destroy();
        flush();
    }));

    it('should not try to query test cases or solution participation results if the exercise is being created (there can be no test cases yet)', fakeAsync(() => {
        comp.editMode = false;
        const newExercise = { ...exercise, id: undefined };

        setRequiredInputs(fixture, newExercise as ProgrammingExercise);

        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).toHaveLength(0);

        expect(comp.testCaseSubscription).toBeUndefined();
        expect(subscribeForTestCaseSpy).not.toHaveBeenCalled();
        expect(getLatestResultWithFeedbacksStub).not.toHaveBeenCalled();

        const saveProblemStatementButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveProblemStatementButton).toBeNull();

        fixture.destroy();
        flush();
    }));

    it('should re-render the preview html when forceRender has emitted', fakeAsync(() => {
        const forceRenderSubject = new Subject<void>();
        setRequiredInputs(fixture, exercise, forceRenderSubject.asObservable());

        fixture.detectChanges();
        tick();

        forceRenderSubject.next();

        expect(generateHtmlSubjectStub).toHaveBeenCalledOnce();

        fixture.destroy();
        flush();
    }));

    it('should update the code editor annotations when receiving a new ProblemStatementAnalysis', fakeAsync(() => {
        const setAnnotationsStub = jest.fn();
        // The component is mocked, so we need to set the monacoEditor property to a mock object.
        comp.markdownEditorMonaco = { monacoEditor: { setAnnotations: setAnnotationsStub } } as unknown as MarkdownEditorMonacoComponent;

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
        flush();
    }));

    it('should save the problem statement to the server', () => {
        comp.editMode = true;
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        const updateProblemStatement = jest.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(of(new HttpResponse({ body: exercise })));

        comp.updateProblemStatement('new problem statement');
        fixture.componentRef.setInput('exercise', { ...exercise, problemStatement: 'new problem statement' } as ProgrammingExercise);
        fixture.detectChanges();
        comp.saveInstructions({ stopPropagation: () => {} } as Event);

        expect(updateProblemStatement).toHaveBeenCalledExactlyOnceWith(exercise.id, 'new problem statement');
    });

    it('should log an error on save', () => {
        const updateProblemStatementSpy = jest.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(throwError(() => undefined));
        const logErrorSpy = jest.spyOn(alertService, 'error');

        comp.editMode = true;
        setRequiredInputs(fixture, exercise);
        fixture.detectChanges();

        comp.saveInstructions(new KeyboardEvent('cmd+s'));
        expect(updateProblemStatementSpy).toHaveBeenCalledOnce();
        expect(logErrorSpy).toHaveBeenCalledOnce();
    });

    it('should save on key commands', () => {
        const saveInstructionsSpy = jest.spyOn(comp, 'saveInstructions');
        comp.editMode = true;
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
});
