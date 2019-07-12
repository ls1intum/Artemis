import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, spy, stub } from 'sinon';
import { of, Subject, Subscription, throwError } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AceEditorModule } from 'ng2-ace-editor';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'src/main/webapp/app/entities/participation';
import { SafeHtmlPipe } from 'src/main/webapp/app/shared';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { Feedback } from 'src/main/webapp/app/entities/feedback';
import { MockResultService } from '../../mocks/mock-result.service';
import {
    ProgrammingExercise,
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseParticipationService,
    ProgrammingExerciseTestCaseService,
    TestCaseState,
} from 'src/main/webapp/app/entities/programming-exercise';
import { RepositoryFileService } from 'src/main/webapp/app/entities/repository';
import { MockRepositoryFileService } from '../../mocks/mock-repository-file.service';
import { problemStatement, problemStatementBubbleSortNotExecutedHtml, problemStatementBubbleSortFailsHtml } from '../../sample/problemStatement.json';
import { MockParticipationWebsocketService } from '../../mocks';
import { MockNgbModalService } from '../../mocks/mock-ngb-modal.service';
import { EditorInstructionsResultDetailComponent } from 'app/code-editor';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { MockProgrammingExerciseParticipationService } from '../../mocks/mock-programming-exercise-participation.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let repositoryFileService: RepositoryFileService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let testCaseService: ProgrammingExerciseTestCaseService;
    let modalService: NgbModal;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getFileStub: SinonStub;
    let openModalStub: SinonStub;
    let getLatestResultWithFeedbacks: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, NgbModule],
            declarations: [ProgrammingExerciseInstructionComponent, SafeHtmlPipe],
            providers: [
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                repositoryFileService = debugElement.injector.get(RepositoryFileService);
                testCaseService = debugElement.injector.get(ProgrammingExerciseTestCaseService);
                modalService = debugElement.injector.get(NgbModal);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                openModalStub = stub(modalService, 'open');
                getFileStub = stub(repositoryFileService, 'get');
                getLatestResultWithFeedbacks = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        openModalStub.restore();
        getFileStub.restore();
        getLatestResultWithFeedbacks.restore();
    });

    it('should on participation change clear old subscription for participation results set up new one', () => {
        const oldParticipation = { id: 1 };
        const result = { id: 1 };
        const participation = { id: 2, results: [result] } as Participation;
        const oldSubscription = new Subscription();
        subscribeForLatestResultOfParticipationStub.returns(of());
        comp.participation = participation;
        comp.participationSubscription = oldSubscription;
        const changes: SimpleChanges = {
            participation: new SimpleChange(oldParticipation, participation, false),
        };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(participation.id);
        expect(comp.participationSubscription).not.to.equal(oldSubscription);
        expect(comp.isInitial).to.be.true;
    });

    it('should try to fetch README.md from assignment repository if no problemStatement was provided', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const getFileSubject = new Subject<{ fileContent: string; fileName: string }>();
        const loadInitialResultStub = stub(comp, 'loadInitialResult').returns(of(result));
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        getFileStub.returns(getFileSubject);
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        comp.ngOnChanges({} as SimpleChanges);
        fixture.detectChanges();
        expect(comp.isLoading).to.be.true;
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).to.exist;
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.to.exist;
        expect(getFileStub).to.have.been.calledOnceWithExactly(participation.id, 'README.md');

        getFileSubject.next({ fileContent: 'lorem ipsum', fileName: 'README.md' });
        expect(comp.problemStatement).to.equal('lorem ipsum');
        expect(loadInitialResultStub).to.have.been.calledOnceWithExactly();
        expect(comp.latestResult).to.deep.equal(result);
        expect(updateMarkdownStub).to.have.been.calledOnceWithExactly();
        expect(comp.isInitial).to.be.false;
        expect(comp.isLoading).to.be.false;
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).not.to.exist;
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).to.exist;
    });

    it('should NOT try to fetch README.md from assignment repository if a problemStatement was provided', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const problemStatement = 'lorem ipsum';
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;
        const loadInitialResultStub = stub(comp, 'loadInitialResult').returns(of(result));
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        comp.ngOnChanges({} as SimpleChanges);

        expect(getFileStub).to.not.have.been.called;
        expect(comp.problemStatement).to.equal('lorem ipsum');
        expect(loadInitialResultStub).to.have.been.calledOnceWithExactly();
        expect(comp.latestResult).to.deep.equal(result);
        expect(updateMarkdownStub).to.have.been.calledOnceWithExactly();
        expect(comp.isInitial).to.be.false;
        expect(comp.isLoading).to.be.false;
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).not.to.exist;
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).to.exist;
    });

    it('should emit that no instructions are available if there is neither a problemStatement provided nor a README.md can be retrieved', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const getFileSubject = new Subject<{ fileContent: string; fileName: string }>();
        const loadInitialResultStub = stub(comp, 'loadInitialResult').returns(of(result));
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        const noInstructionsAvailableSpy = spy(comp.onNoInstructionsAvailable, 'emit');
        getFileStub.returns(getFileSubject);
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        comp.ngOnChanges({} as SimpleChanges);
        expect(comp.isLoading).to.be.true;
        expect(getFileStub).to.have.been.calledOnceWithExactly(participation.id, 'README.md');

        getFileSubject.error('fatal error');
        expect(comp.problemStatement).to.be.undefined;
        expect(loadInitialResultStub).to.not.have.been.called;
        expect(comp.latestResult).to.be.undefined;
        expect(updateMarkdownStub).to.not.have.been.called;
        expect(noInstructionsAvailableSpy).to.have.been.calledOnceWithExactly();
        expect(comp.isInitial).to.be.false;
        expect(comp.isLoading).to.be.false;
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).not.to.exist;
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).to.exist;
    });

    it('should update markdown if the problemStatement is changed', () => {
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const oldProblemStatement = 'lorem ipsum';
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        const loadInitialResult = stub(comp, 'loadInitialResult');
        fixture.detectChanges();
        comp.exercise = { ...exercise, problemStatement: newProblemStatement };
        comp.participation = participation;
        comp.isInitial = false;
        comp.ngOnChanges({
            exercise: {
                previousValue: { ...exercise, problemStatement: oldProblemStatement },
                currentValue: { ...this.exercise, problemStatement: newProblemStatement },
                firstChange: false,
            } as SimpleChange,
        } as SimpleChanges);
        expect(updateMarkdownStub).to.have.been.calledOnceWithExactly();
        expect(loadInitialResult).not.to.have.been.called;
    });

    it('should initially update the markdown if there is no participation and the exercise has changed', () => {
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        const loadInitialResult = stub(comp, 'loadInitialResult');
        fixture.detectChanges();
        comp.exercise = { ...exercise, problemStatement: newProblemStatement };
        comp.participation = participation;
        comp.isInitial = false;
        comp.ngOnChanges({
            exercise: {
                previousValue: undefined,
                currentValue: { ...this.exercise, problemStatement: newProblemStatement },
                firstChange: false,
            } as SimpleChange,
        } as SimpleChanges);
        expect(updateMarkdownStub).to.have.been.calledOnceWithExactly();
        expect(loadInitialResult).not.to.have.been.called;
    });

    it('should still render the instructions if fetching the latest result fails', () => {
        const participation = { id: 2 } as Participation;
        const problemStatement = 'lorem ipsum';
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        getLatestResultWithFeedbacks.returns(throwError('fatal error'));
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        comp.ngOnChanges({} as SimpleChanges);

        expect(getLatestResultWithFeedbacks).to.have.been.calledOnceWith(participation.id);
        expect(updateMarkdownStub).to.have.been.calledOnce;
        expect(comp.isInitial).to.be.false;
        expect(comp.isLoading).to.be.false;
    });

    it('should create the steps task icons for the tasks in problem statement markdown (non legacy case)', fakeAsync(() => {
        const result = {
            id: 1,
            completionDate: moment('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testMergeSort', detail_text: 'lorem ipsum', positive: true }],
        } as any;
        const testCases = [{ testName: 'testBubbleSort', active: true }, { testName: 'testMergeSort', active: true }];
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;

        openModalStub.returns({ componentInstance: {} });
        comp.problemStatement = exercise.problemStatement;
        comp.exercise = exercise;
        comp.latestResult = result;
        comp.exerciseTestCases = testCases.map(({ testName }: { testName: string }) => testName);

        comp.updateMarkdown();

        expect(comp.steps).to.have.lengthOf(2);
        expect(comp.steps[0]).to.deep.equal({ title: 'Implement Bubble Sort', done: TestCaseState.NOT_EXECUTED });
        expect(comp.steps[1]).to.deep.equal({ title: 'Implement Merge Sort', done: TestCaseState.SUCCESS });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).to.exist;
        expect(debugElement.queryAll(By.css('.stepwizard-circle'))).to.have.lengthOf(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).to.equal(problemStatementBubbleSortNotExecutedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).to.exist;
        expect(mergeSortStep).to.exist;
        bubbleSortStep.nativeElement.click();
        mergeSortStep.nativeElement.click();

        expect(openModalStub).to.have.been.calledOnceWithExactly(EditorInstructionsResultDetailComponent, { keyboard: true, size: 'lg' });
    }));

    it('should create the steps task icons for the tasks in problem statement markdown (legacy case)', fakeAsync(() => {
        const result = {
            id: 1,
            completionDate: moment('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum' }],
        } as any;
        const testCases = [{ testName: 'testBubbleSort', active: true }, { testName: 'testMergeSort', active: true }];
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;

        openModalStub.returns({ componentInstance: {} });
        comp.problemStatement = exercise.problemStatement;
        comp.exercise = exercise;
        comp.latestResult = result;
        comp.exerciseTestCases = testCases.map(({ testName }: { testName: string }) => testName);

        comp.updateMarkdown();

        expect(comp.steps).to.have.lengthOf(2);
        expect(comp.steps[0]).to.deep.equal({ title: 'Implement Bubble Sort', done: TestCaseState.FAIL });
        expect(comp.steps[1]).to.deep.equal({ title: 'Implement Merge Sort', done: TestCaseState.SUCCESS });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).to.exist;
        expect(debugElement.queryAll(By.css('.stepwizard-circle'))).to.have.lengthOf(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).to.equal(problemStatementBubbleSortFailsHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--failed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).to.exist;
        expect(mergeSortStep).to.exist;
        bubbleSortStep.nativeElement.click();
        mergeSortStep.nativeElement.click();

        expect(openModalStub).to.have.been.calledOnce;
        expect(openModalStub).to.have.been.calledOnceWithExactly(EditorInstructionsResultDetailComponent, { keyboard: true, size: 'lg' });
    }));

    it('should determine a successful state for all tasks if the result is successful', () => {
        const result = {
            id: 1,
            completionDate: moment('2019-06-06T22:15:29.203+02:00'),
            successful: true,
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum', positive: true }, { text: 'testMergeSort', detail_text: 'lorem ipsum', positive: true }],
        } as any;
        const testCases = result.feedbacks.map(({ text }: { text: string }) => text);

        comp.latestResult = result;

        const [taskState1] = comp.statusForTests(testCases.slice(0, 1));
        expect(taskState1).to.equal(TestCaseState.SUCCESS);

        const [taskState2] = comp.statusForTests(testCases.slice(2));
        expect(taskState2).to.equal(TestCaseState.SUCCESS);
    });

    it('should determine a failed state for a task if at least one test has failed (non legacy case)', () => {
        const result = {
            id: 1,
            completionDate: moment('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum', positive: false }, { text: 'testMergeSort', detail_text: 'lorem ipsum', positive: true }],
        } as any;
        const testCases = result.feedbacks.map(({ text }: { text: string }) => text);

        comp.latestResult = result;

        const [taskState1] = comp.statusForTests(testCases);
        expect(taskState1).to.equal(TestCaseState.FAIL);
    });

    it('should determine a failed state for a task if at least one test has failed (legacy case)', () => {
        const result = {
            id: 1,
            completionDate: moment('2018-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum', positive: false }],
        } as any;
        const testCases = ['testBubbleSort', 'testMergeSort'];

        comp.latestResult = result;

        const [taskState1] = comp.statusForTests(testCases);
        expect(taskState1).to.equal(TestCaseState.FAIL);
    });

    it('should determine a state if there is no feedback for the specified tests (non legacy only)', () => {
        const result = {
            id: 1,
            completionDate: moment('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ text: 'irrelevantTest', detail_text: 'lorem ipsum', positive: true }],
        } as any;
        const testCases = ['testBubbleSort', 'testMergeSort'];

        comp.latestResult = result;

        const [taskState1] = comp.statusForTests(testCases);
        expect(taskState1).to.equal(TestCaseState.NOT_EXECUTED);
    });
});
