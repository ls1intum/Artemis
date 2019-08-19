import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { of, Subject } from 'rxjs';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement, SimpleChanges, SimpleChange } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'src/main/webapp/app/entities/participation';
import { SafeHtmlPipe } from 'src/main/webapp/app/shared';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { MockResultService } from '../../mocks/mock-result.service';
import {
    ProgrammingExercise,
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseInstructionTestcaseStatusComponent,
    ProgrammingExerciseParticipationService,
    ProgrammingExerciseTestCaseService,
} from 'src/main/webapp/app/entities/programming-exercise';
import { MockParticipationWebsocketService } from '../../mocks';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';

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

    const exercise = { id: 30, templateParticipation: { id: 99 } } as ProgrammingExercise;
    const participation = { id: 1, results: [{ id: 10, feedbacks: [{ id: 20 }, { id: 21 }] }] } as Participation;
    const testCases = [{ testName: 'test1', active: true }, { testName: 'test2', active: true }, { testName: 'test3', active: false }];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, NgbModule],
            declarations: [
                ProgrammingExerciseInstructionStepWizardComponent,
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionTestcaseStatusComponent),
                MockComponent(MarkdownEditorComponent),
                ProgrammingExerciseInstructionComponent,
                SafeHtmlPipe,
            ],
            providers: [
                ProgrammingExerciseInstructionService,
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
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
            });
    });

    afterEach(() => {
        (testCaseService as MockProgrammingExerciseTestCaseService).initSubject([]);
        subscribeForTestCaseSpy.restore();
        getLatestResultWithFeedbacksStub.restore();
    });

    it('should not have any test cases if the test case service emits an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        const changes: SimpleChanges = {
            exercise: new SimpleChange(undefined, exercise, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(0);
    }));

    it('should have test cases according to the result of the test case service if it does not return an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        const changes: SimpleChanges = {
            exercise: new SimpleChange(undefined, exercise, true),
        };
        comp.ngOnChanges(changes);

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases);

        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);
    }));

    it('should update test cases if a new test case result comes in', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        const changes: SimpleChanges = {
            exercise: new SimpleChange(undefined, exercise, true),
        };
        comp.ngOnChanges(changes);

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
    }));

    it('should try to retreive the test case values from the solution repos last build result if there are no testCases (empty result)', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;
        const subject = new Subject<Result>();
        getLatestResultWithFeedbacksStub.returns(subject);

        const changes: SimpleChanges = {
            exercise: new SimpleChange(undefined, exercise, true),
        };
        comp.ngOnChanges(changes);

        // No test cases available, might be that the solution build never ran to create tests...
        (testCaseService as MockProgrammingExerciseTestCaseService).next(null);

        fixture.detectChanges();

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(getLatestResultWithFeedbacksStub).to.have.been.calledOnceWithExactly(exercise.templateParticipation.id);

        subject.next({ feedbacks: [{ text: 'testY' }, { text: 'testX' }] } as Result);
        tick();

        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['testX', 'testY']);
    }));

    it('should not try to query test cases or solution participation results if the exercise is being created (there can be no test cases yet)', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;
        comp.editMode = false;

        const changes: SimpleChanges = {
            exercise: new SimpleChange(undefined, exercise, true),
        };
        comp.ngOnChanges(changes);

        fixture.detectChanges();
        tick();

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(comp.exerciseTestCases).to.be.empty;

        expect(comp.testCaseSubscription).to.be.undefined;
        expect(subscribeForTestCaseSpy).not.to.have.been.called;
        expect(getLatestResultWithFeedbacksStub).not.to.have.been.called;

        const saveProblemStatementButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveProblemStatementButton).not.to.exist;
    }));
});
