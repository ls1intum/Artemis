import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, spy, stub } from 'sinon';
import { Observable, of, Subject, Subscription, throwError } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTestModule } from '../../test.module';
import { Participation } from 'src/main/webapp/app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { ArtemisSharedModule } from 'src/main/webapp/app/shared';
import { Result, ResultDetailComponent, ResultService } from 'src/main/webapp/app/entities/result';
import { Feedback } from 'src/main/webapp/app/entities/feedback';
import { MockResultService } from '../../mocks/mock-result.service';
import { ProgrammingExercise } from 'src/main/webapp/app/entities/programming-exercise';
import { RepositoryFileService } from 'src/main/webapp/app/entities/repository';
import { MockRepositoryFileService } from '../../mocks/mock-repository-file.service';
import { problemStatement, problemStatementBubbleSortFailsHtml, problemStatementBubbleSortNotExecutedHtml } from '../../sample/problemStatement.json';
import { MockExerciseHintService, MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { MockNgbModalService } from '../../mocks/mock-ngb-modal.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/entities/programming-exercise/instructions/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/entities/programming-exercise/instructions/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { MockProgrammingExerciseParticipationService } from '../../mocks/mock-programming-exercise-participation.service';
import { ExerciseHintService, IExerciseHintService } from 'app/entities/exercise-hint';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionTaskStatusComponent } from 'app/entities/programming-exercise/instructions/instructions-render';
import { triggerChanges } from '../../utils/general.utils';
import { LocalStorageService } from 'ngx-webstorage';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let repositoryFileService: RepositoryFileService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let exerciseHintService: IExerciseHintService;
    let modalService: NgbModal;

    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getFileStub: SinonStub;
    let openModalStub: SinonStub;
    let getLatestResultWithFeedbacks: SinonStub;
    let getHintsForExerciseStub: SinonStub;

    const exerciseHints = [{ id: 1 }, { id: 2 }];
    const exercise = { id: 1 };

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, NgbModule],
            declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
            providers: [
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                ProgrammingExerciseInstructionService,
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .overrideModule(BrowserDynamicTestingModule, { set: { entryComponents: [FaIconComponent, ProgrammingExerciseInstructionTaskStatusComponent] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                repositoryFileService = debugElement.injector.get(RepositoryFileService);
                exerciseHintService = debugElement.injector.get(ExerciseHintService);
                modalService = debugElement.injector.get(NgbModal);

                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                openModalStub = stub(modalService, 'open');
                getFileStub = stub(repositoryFileService, 'get');
                getLatestResultWithFeedbacks = stub(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        openModalStub.restore();
        getFileStub.restore();
        getLatestResultWithFeedbacks.restore();
    });

    it('should on participation change clear old subscription for participation results set up new one', () => {
        const oldParticipation = { id: 1 } as Participation;
        const result = { id: 1 };
        const participation = { id: 2, results: [result] } as Participation;
        const oldSubscription = new Subscription();
        subscribeForLatestResultOfParticipationStub.returns(of());
        comp.exercise = exercise as ProgrammingExercise;
        comp.participation = participation;
        comp.participationSubscription = oldSubscription;

        triggerChanges(comp, { property: 'participation', currentValue: participation, previousValue: oldParticipation, firstChange: false });
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(participation.id);
        expect(comp.participationSubscription).not.to.equal(oldSubscription);
        expect(comp.isInitial).to.be.true;
        expect(getHintsForExerciseStub).to.have.been.calledOnceWithExactly(exercise.id);
        expect(comp.exerciseHints).to.deep.equal(exerciseHints);
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
        triggerChanges(comp);
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
        triggerChanges(comp);

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
        triggerChanges(comp);
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

    it('should NOT update markdown if the problemStatement is changed', () => {
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
        triggerChanges(comp, {
            property: 'exercise',
            previousValue: { ...exercise, problemStatement: oldProblemStatement },
            currentValue: { ...comp.exercise, problemStatement: newProblemStatement },
            firstChange: false,
        });
        expect(updateMarkdownStub).to.have.been.called;
        expect(loadInitialResult).not.to.have.been.called;
    });

    it('should NOT update the markdown if there is no participation and the exercise has changed', () => {
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        const loadInitialResult = stub(comp, 'loadInitialResult');
        fixture.detectChanges();
        comp.exercise = { ...exercise, problemStatement: newProblemStatement };
        comp.participation = participation;
        comp.isInitial = false;
        triggerChanges(comp, { property: 'exercise', currentValue: { ...comp.exercise, problemStatement: newProblemStatement }, firstChange: false });
        expect(comp.markdownExtensions).to.have.lengthOf(2);
        expect(updateMarkdownStub).to.have.been.called;
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
        triggerChanges(comp);

        expect(comp.markdownExtensions).to.have.lengthOf(2);
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
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;

        openModalStub.returns({ componentInstance: {} });
        comp.problemStatement = exercise.problemStatement;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).to.have.lengthOf(2);
        expect(comp.tasks[0]).to.deep.equal({
            completeString: '[task][Implement Bubble Sort](testBubbleSort)',
            taskName: 'Implement Bubble Sort',
            tests: ['testBubbleSort'],
            hints: [],
        });
        expect(comp.tasks[1]).to.deep.equal({
            completeString: '[task][Implement Merge Sort](testMergeSort){33,44}',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
            hints: ['33', '44'],
        });
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

        expect(openModalStub).to.have.been.calledOnceWithExactly(ResultDetailComponent, { keyboard: true, size: 'lg' });
    }));

    it('should create the steps task icons for the tasks in problem statement markdown (legacy case)', fakeAsync(() => {
        const result = {
            id: 1,
            completionDate: moment('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum' }],
        } as any;
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;

        openModalStub.returns({ componentInstance: {} });
        comp.problemStatement = exercise.problemStatement;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).to.have.lengthOf(2);
        expect(comp.tasks[0]).to.deep.equal({
            completeString: '[task][Implement Bubble Sort](testBubbleSort)',
            taskName: 'Implement Bubble Sort',
            tests: ['testBubbleSort'],
            hints: [],
        });
        expect(comp.tasks[1]).to.deep.equal({
            completeString: '[task][Implement Merge Sort](testMergeSort){33,44}',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
            hints: ['33', '44'],
        });
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
        expect(openModalStub).to.have.been.calledOnceWithExactly(ResultDetailComponent, { keyboard: true, size: 'lg' });
    }));
});
