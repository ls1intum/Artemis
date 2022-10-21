import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Participation } from 'app/entities/participation/participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { IProgrammingExerciseGradingService, ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Subject } from 'rxjs';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../test.module';

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let gradingService: IProgrammingExerciseGradingService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

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

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionAnalysisComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                gradingService = debugElement.injector.get(ProgrammingExerciseGradingService);
                (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                subscribeForTestCaseSpy = jest.spyOn(gradingService, 'subscribeForTestCases');
                getLatestResultWithFeedbacksStub = jest.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                generateHtmlSubjectStub = jest.spyOn(comp.generateHtmlSubject, 'next');
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
        fixture.detectChanges();
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

        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).toHaveBeenNthCalledWith(1, exercise.id);
        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);

        fixture.destroy();
        flush();
    }));

    it('should update test cases if a new test case result comes in', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(testCases);

        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).toHaveLength(2);
        expect(comp.exerciseTestCases).toEqual(['test1', 'test2']);

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases([{ testName: 'testX' }]);
        fixture.detectChanges();
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

        fixture.detectChanges();

        expect(comp.exerciseTestCases).toHaveLength(0);
        expect(getLatestResultWithFeedbacksStub).toHaveBeenNthCalledWith(1, exercise.templateParticipation!.id!);

        subject.next({ feedbacks: [{ text: 'testY' }, { text: 'testX' }] } as Result);
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
        comp.exercise = exercise;
        comp.participation = participation;
        comp.forceRender = forceRenderSubject.asObservable();

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        fixture.detectChanges();
        tick();

        forceRenderSubject.next();

        expect(generateHtmlSubjectStub).toHaveBeenCalledOnce();

        fixture.destroy();
        flush();
    }));

    it('should update the code editor annotations when receiving a new ProblemStatementAnalysis', fakeAsync(() => {
        const session = {
            clearAnnotations: jest.fn(),
            setAnnotations: jest.fn(),
        };
        const editor = {
            getSession: () => session,
        };
        const aceEditorContainer = {
            getEditor: () => editor,
        };
        // @ts-ignore
        comp.markdownEditor = { aceEditorContainer };

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
        tick();

        expect(session.clearAnnotations).toHaveBeenCalledOnce();
        expect(session.setAnnotations).toHaveBeenCalledOnce();
        expect(session.setAnnotations).toHaveBeenCalledWith(expectedWarnings);

        fixture.destroy();
        flush();
    }));
});
