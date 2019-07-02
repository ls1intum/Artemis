import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonSpy, spy } from 'sinon';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'src/main/webapp/app/entities/participation';
import { SafeHtmlPipe } from 'src/main/webapp/app/shared';
import { ResultService } from 'src/main/webapp/app/entities/result';
import { MockResultService } from '../../mocks/mock-result.service';
import {
    ProgrammingExercise,
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseInstructionTestcaseStatusComponent,
    ProgrammingExerciseTestCaseService,
} from 'src/main/webapp/app/entities/programming-exercise';
import { MockParticipationWebsocketService } from '../../mocks';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let testCaseService: ProgrammingExerciseTestCaseService;

    let subscribeForTestCaseSpy: SinonSpy;

    const exercise = { id: 30 } as ProgrammingExercise;
    const participation = { id: 1, results: [{ id: 10, feedbacks: [{ id: 20 }, { id: 21 }] }] } as Participation;
    const testCases = [{ testName: 'test1', active: true }, { testName: 'test2', active: true }, { testName: 'test3', active: false }];

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, NgbModule],
            declarations: [
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionTestcaseStatusComponent),
                MockComponent(MarkdownEditorComponent),
                ProgrammingExerciseInstructionComponent,
                SafeHtmlPipe,
            ],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
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
                subscribeForTestCaseSpy = spy(testCaseService, 'subscribeForTestCases');
            });
    });

    afterEach(() => {
        (testCaseService as MockProgrammingExerciseTestCaseService).initSubject([]);
        subscribeForTestCaseSpy.restore();
    });

    it('should not have any test cases if the test case service emits an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

        fixture.detectChanges();
        tick();

        expect(subscribeForTestCaseSpy).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(0);
    }));

    it('should have test cases according to the result of the test case service if it does not return an empty array', fakeAsync(() => {
        comp.exercise = exercise;
        comp.participation = participation;

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
});
