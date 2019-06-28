import { fakeAsync, ComponentFixture, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent } from 'ng-mocks';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, stub } from 'sinon';
import { BehaviorSubject, of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AceEditorModule } from 'ng2-ace-editor';
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
    ProgrammingExerciseService,
} from 'src/main/webapp/app/entities/programming-exercise';
import { MockParticipationWebsocketService } from '../../mocks';
import { MarkdownEditorComponent } from 'app/markdown-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseEditableInstructionComponent', () => {
    let comp: ProgrammingExerciseEditableInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseEditableInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let resultService: ResultService;
    let programmingExerciseService: ProgrammingExerciseService;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getLatestResultWithFeedbacksStub: SinonStub;
    let latestResultSubject: BehaviorSubject<Result>;
    let updateProblemStatementStub: SinonStub;

    const exercise = { id: 30 } as ProgrammingExercise;
    const participation = { id: 1, results: [{ id: 10, feedbacks: [{ id: 20 }, { id: 21 }] }] } as Participation;
    const templateparticipation = { id: 1, results: [{ id: 10 }] } as Participation;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, NgbModule],
            declarations: [
                ProgrammingExerciseEditableInstructionComponent,
                MockComponent(ProgrammingExerciseInstructionTestcaseStatusComponent),
                MockComponent(MarkdownEditorComponent),
                MockComponent(ProgrammingExerciseInstructionComponent),
                SafeHtmlPipe,
            ],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                ProgrammingExerciseService,
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseEditableInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                resultService = debugElement.injector.get(ResultService);

                latestResultSubject = new BehaviorSubject(null);

                getLatestResultWithFeedbacksStub = stub(resultService, 'getLatestResultWithFeedbacks');
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(latestResultSubject);

                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                updateProblemStatementStub = stub(programmingExerciseService, 'updateProblemStatement');
            });
    });

    afterEach(() => {
        getLatestResultWithFeedbacksStub.restore();
        subscribeForLatestResultOfParticipationStub.restore();

        latestResultSubject.complete();
        latestResultSubject = new BehaviorSubject(null);
        subscribeForLatestResultOfParticipationStub.returns(latestResultSubject);
        updateProblemStatementStub.restore();
    });

    it('should not have any test cases if the result feedbacks of the template participation are empty', () => {
        comp.exercise = exercise;
        comp.participation = participation;
        comp.templateParticipation = templateparticipation;

        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
            templateParticipation: new SimpleChange(undefined, comp.templateParticipation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(templateparticipation.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(0);
    });

    it('should have test cases if the result feedbacks of the template participation is not empty', () => {
        comp.exercise = exercise;
        comp.participation = participation;
        comp.templateParticipation = { ...templateparticipation, results: [{ id: 20, feedbacks: [{ text: 'test1' }, { text: 'test2' }] }] } as Participation;

        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
            templateParticipation: new SimpleChange(undefined, comp.templateParticipation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(templateparticipation.id);
        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);
    });

    it('should update test cases if new templateParticipation result comes in', () => {
        comp.exercise = exercise;
        comp.participation = participation;
        comp.templateParticipation = templateparticipation;
        const newResult = { id: 20, feedbacks: [{ text: 'test1' }, { text: 'test2' }] } as Result;

        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
            templateParticipation: new SimpleChange(undefined, comp.templateParticipation, true),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(comp.exerciseTestCases).to.have.lengthOf(0);
        expect(comp.exerciseTestCases).to.be.empty;

        latestResultSubject.next(newResult);
        fixture.detectChanges();

        expect(comp.exerciseTestCases).to.have.lengthOf(2);
        expect(comp.exerciseTestCases).to.deep.equal(['test1', 'test2']);

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(templateparticipation.id);
    });

    it('should update programming exercise problem statement on button click', fakeAsync((done: any) => {
        const newProblemStatement = 'new lorem ipsum';
        updateProblemStatementStub.returns(of(null));
        comp.exercise = exercise;
        comp.participation = participation;
        comp.templateParticipation = templateparticipation;
        fixture.detectChanges();

        comp.updateProblemStatement(newProblemStatement);
        fixture.detectChanges();

        const saveInstructionsButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveInstructionsButton).to.exist;
        expect(saveInstructionsButton.nativeElement.disabled).to.be.false;

        saveInstructionsButton.nativeElement.click();
        tick();
        fixture
            .whenStable()
            .then(() => {
                expect(updateProblemStatementStub).to.have.been.calledOnce;
                expect(updateProblemStatementStub).to.have.been.calledOnceWithExactly(exercise.id, newProblemStatement);
                expect(comp.unsavedChanges).to.be.false;
            })
            .catch(err => done.fail(err));
    }));
});
