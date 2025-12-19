import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { DebugElement } from '@angular/core';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { triggerChanges } from 'test/helpers/utils/general-test.utils';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
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
    const participation = { id: 1, results: [{ id: 10, feedbacks: [{ id: 20 }, { id: 21 }] }] } as Participation;
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
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ProfileService, {
                    getProfileInfo: () => mockProfileInfo,
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
            });
    });

    afterEach(() => {
        (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
        jest.restoreAllMocks();
    });

    it('should not have any test cases if the test case service emits an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });
        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);
        expect(comp.exerciseTestCases).toHaveLength(0);

        fixture.destroy();
        flush();
    }));

    it('should have test cases according to the result of the test case service if it does not return an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);

        fixture.changeDetectorRef.detectChanges();
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
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);

        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases([{ testName: 'testX' }]);
        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(comp.exerciseTestCases).toHaveLength(0);

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);

        fixture.destroy();
        flush();
    }));

    it('should try to retrieve the test case values from the solution repos last build result if there are no testCases (empty result)', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;
        const subject = new Subject<Result>();
        getLatestResultWithFeedbacksStub.mockReturnValue(subject);

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        // No test cases available, might be that the solution build never ran to create tests...
        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(undefined);

        fixture.changeDetectorRef.detectChanges();

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
        comp.exercise = exercise;
        comp.participation = participation;
        comp.editMode = false;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        fixture.changeDetectorRef.detectChanges();
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
        comp.exercise = exercise;
        comp.participation = participation;
        comp.forceRender = forceRenderSubject.asObservable();

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        fixture.changeDetectorRef.detectChanges();
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
        comp.exercise = exercise;
        comp.editMode = true;

        const updateProblemStatement = jest.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(of(new HttpResponse({ body: exercise })));

        comp.updateProblemStatement('new problem statement');
        comp.saveInstructions({ stopPropagation: () => {} } as Event);

        expect(updateProblemStatement).toHaveBeenCalledExactlyOnceWith(exercise.id, 'new problem statement');
    });

    it('should log an error on save', () => {
        const updateProblemStatementSpy = jest.spyOn(programmingExerciseService, 'updateProblemStatement').mockReturnValue(throwError(() => undefined));
        const logErrorSpy = jest.spyOn(alertService, 'error');

        comp.exercise = exercise;
        comp.editMode = true;

        comp.saveInstructions(new KeyboardEvent('cmd+s'));
        expect(updateProblemStatementSpy).toHaveBeenCalledOnce();
        expect(logErrorSpy).toHaveBeenCalledOnce();
    });

    it('should save on key commands', () => {
        const saveInstructionsSpy = jest.spyOn(comp, 'saveInstructions');
        comp.exercise = exercise;
        comp.editMode = true;

        comp.saveOnControlAndS(new KeyboardEvent('ctrl+s'));
        expect(saveInstructionsSpy).toHaveBeenCalledOnce();

        comp.saveOnCommandAndS(new KeyboardEvent('cmd+s'));
        expect(saveInstructionsSpy).toHaveBeenCalledOnce();
    });

    it('should have intelligence actions when Hyperion is active', () => {
        const isModuleFeatureActiveSpy = jest.spyOn(TestBed.inject(ProfileService), 'isModuleFeatureActive').mockReturnValue(true);

        // Komponente erneut erzeugen, damit computed() neu berechnet wird
        fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
        comp = fixture.componentInstance;

        // IDs setzen, die in artemisIntelligenceActions verwendet werden
        comp.courseId = 1;
        comp.exerciseId = 42;

        fixture.changeDetectorRef.detectChanges();

        const actions = comp.artemisIntelligenceActions();
        expect(actions).toHaveLength(1);
        expect(actions[0]).toBeInstanceOf(RewriteAction);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_HYPERION);
    });

    it('should cleanup subscriptions on destroy', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });
        fixture.changeDetectorRef.detectChanges();
        tick();

        // Get subscription reference before destroy
        const testCaseSubscription = comp.testCaseSubscription;

        // Destroy the component
        comp.ngOnDestroy();

        // Verify cleanup occurred
        if (testCaseSubscription) {
            expect(testCaseSubscription.closed).toBeTrue();
        }

        flush();
    }));

    it('should subscribe for test cases when exercise changes', fakeAsync(() => {
        const newExercise = { ...exercise, id: 31 } as ProgrammingExercise;
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });
        fixture.changeDetectorRef.detectChanges();
        tick();

        // Reset spy
        generateHtmlSubjectStub.mockClear();

        // Trigger exercise change
        comp.exercise = newExercise;
        triggerChanges(comp, { property: 'exercise', currentValue: newExercise, previousValue: exercise });
        fixture.changeDetectorRef.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).toHaveBeenCalledWith(newExercise.id);

        fixture.destroy();
        flush();
    }));
});
