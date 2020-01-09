import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { Subject } from 'rxjs';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { Participation, TemplateProgrammingExerciseParticipation } from 'src/main/webapp/app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { MockResultService } from '../../mocks';
import { ProgrammingExercise } from 'src/main/webapp/app/entities/programming-exercise';
import { MockParticipationWebsocketService } from '../../mocks';
import { MarkdownEditorComponent } from 'app/markdown-editor/markdown-editor.component';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import {
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionAnalysisComponent,
} from 'app/entities/programming-exercise/instructions/instructions-editor';
import { triggerChanges } from '../../utils/general.utils';
import { ProgrammingExerciseParticipationService, ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let testCaseService: ProgrammingExerciseTestCaseService;
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
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                testCaseService = debugElement.injector.get(ProgrammingExerciseTestCaseService);
                (testCaseService as MockProgrammingExerciseTestCaseService).initSubject([]);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                subscribeForTestCaseSpy = spy(testCaseService, 'subscribeForTestCases');
                getLatestResultWithFeedbacksStub = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                generateHtmlSubjectStub = stub(comp.generateHtmlSubject, 'next');
            });
    });

    afterEach(() => {
        (testCaseService as MockProgrammingExerciseTestCaseService).initSubject([]);
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

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases);

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

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases);

        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);

        (testCaseService as MockProgrammingExerciseTestCaseService).next([{ testName: 'testX' }]);
        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).to.be.empty;

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);

        fixture.destroy();
        flush();
    }));

    it('should try to retreive the test case values from the solution repos last build result if there are no testCases (empty result)', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;
        const subject = new Subject<Result>();
        getLatestResultWithFeedbacksStub.returns(subject);

        triggerChanges(comp, { property: 'exercise', currentValue: exercise });

        // No test cases available, might be that the solution build never ran to create tests...
        (testCaseService as MockProgrammingExerciseTestCaseService).next(null);

        fixture.detectChanges();

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(getLatestResultWithFeedbacksStub).to.have.been.calledOnceWithExactly(exercise.templateParticipation.id);

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
