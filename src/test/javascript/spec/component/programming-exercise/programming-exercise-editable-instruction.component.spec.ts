import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { Subject } from 'rxjs';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { Participation } from 'app/entities/participation/participation.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseGradingService, IProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseInstructionAnalysisComponent } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let gradingService: IProgrammingExerciseGradingService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

    let subscribeForTestCaseSpy: SinonSpy;
    let getLatestResultWithFeedbacksStub: SinonStub;
    let generateHtmlSubjectStub: SinonStub;

    const templateParticipation = new TemplateProgrammingExerciseParticipation();
    templateParticipation.id = 99;

    const exercise = { id: 30, templateParticipation } as ProgrammingExercise;
    const participation = { id: 1, results: [{ id: 10, feedbacks: [{ id: 20 }, { id: 21 }] }] } as Participation;
    const testCases = [
        { testName: 'test1', active: true },
        { testName: 'test2', active: true },
        { testName: 'test3', active: false },
    ];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, NgbModule, ArtemisProgrammingExerciseInstructionsEditorModule],
            declarations: [MockComponent(ProgrammingExerciseInstructionAnalysisComponent), MockComponent(MarkdownEditorComponent)],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                gradingService = debugElement.injector.get(ProgrammingExerciseGradingService);
                (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                subscribeForTestCaseSpy = spy(gradingService, 'subscribeForTestCases');
                getLatestResultWithFeedbacksStub = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                generateHtmlSubjectStub = stub(comp.generateHtmlSubject, 'next');
            });
    });

    afterEach(() => {
        (gradingService as MockProgrammingExerciseGradingService).initSubject([]);
        subscribeForTestCaseSpy.restore();
        getLatestResultWithFeedbacksStub.restore();
        generateHtmlSubjectStub.restore();
    });

    it('should not have any test cases if the test case service emits an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });
        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(0);

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

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);

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

        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);

        (gradingService as MockProgrammingExerciseGradingService).nextTestCases([{ testName: 'testX' }]);
        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).to.be.empty;

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);

        fixture.destroy();
        flush();
    }));

    it('should try to retrieve the test case values from the solution repos last build result if there are no testCases (empty result)', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;
        const subject = new Subject<Result>();
        getLatestResultWithFeedbacksStub.returns(subject);

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        // No test cases available, might be that the solution build never ran to create tests...
        (gradingService as MockProgrammingExerciseGradingService).nextTestCases(undefined);

        fixture.detectChanges();

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(getLatestResultWithFeedbacksStub).to.have.been.calledOnceWithExactly(exercise.templateParticipation!.id!);

        subject.next({ feedbacks: [{ text: 'testY' }, { text: 'testX' }] } as Result);
        tick();

        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['testX', 'testY']);

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

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(comp.exerciseTestCases).to.be.empty;

        expect(comp.testCaseSubscription).to.be.undefined;
        expect(subscribeForTestCaseSpy).not.to.have.been.called;
        expect(getLatestResultWithFeedbacksStub).not.to.have.been.called;

        const saveProblemStatementButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveProblemStatementButton).not.to.exist;

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

        expect(generateHtmlSubjectStub).to.have.been.calledOnce;

        fixture.destroy();
        flush();
    }));
});
