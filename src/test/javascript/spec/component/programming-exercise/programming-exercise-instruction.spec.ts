import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DebugElement, SimpleChanges, SimpleChange } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { spy, stub, SinonStub } from 'sinon';
import { Observable, of, Subscription, Subject } from 'rxjs';
import { CodeEditorBuildLogService, CodeEditorSessionService, CodeEditorBuildOutputComponent } from 'src/main/webapp/app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { Participation, ParticipationWebsocketService } from 'src/main/webapp/app/entities/participation';
import { MockCodeEditorBuildLogService } from '../../mocks/mock-code-editor-build-log.service';
import { SafeHtmlPipe } from 'src/main/webapp/app/shared';
import { MockCodeEditorSessionService, MockParticipationWebsocketService } from '../../mocks';
import { Result, ResultService } from 'src/main/webapp/app/entities/result';
import { MockLocalStorageService } from '../../mocks/mock-local-storage.service';
import { Feedback } from 'src/main/webapp/app/entities/feedback';
import { MockResultService } from '../../mocks/mock-result.service';
import { BuildLogEntryArray } from 'src/main/webapp/app/entities/build-log';
import { AnnotationArray } from 'src/main/webapp/app/entities/ace-editor';
import { ProgrammingExercise, ProgrammingExerciseInstructionComponent, TestCaseState } from 'src/main/webapp/app/entities/programming-exercise';
import { RepositoryFileService } from 'src/main/webapp/app/entities/repository';
import { MockRepositoryFileService } from '../../mocks/mock-repository-file.service';
import { problemStatement, problemStatementHtml } from './problemStatement.json';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let repositoryFileService: RepositoryFileService;
    let resultService: ResultService;
    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let getFileStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule],
            declarations: [ProgrammingExerciseInstructionComponent, SafeHtmlPipe],
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                resultService = debugElement.injector.get(ResultService);
                repositoryFileService = debugElement.injector.get(RepositoryFileService);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                getFileStub = stub(repositoryFileService, 'get');
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        getFileStub.restore();
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
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const loadInitialResultStub = stub(comp, 'loadInitialResult').returns(of(result));
        const updateMarkdownStub = stub(comp, 'updateMarkdown');
        const problemStatement = 'lorem ipsum';
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;
        comp.problemStatement = problemStatement;

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

    it('should create the steps for the tasks in problem statement markdown', () => {
        const result = {
            id: 1,
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum', positive: 0 }, { text: 'testMergeSort', detail_text: 'lorem ipsum', positive: 1 }],
        } as any;
        comp.problemStatement = problemStatement;
        comp.latestResult = result;
        comp.updateMarkdown();
        expect(comp.steps).to.have.lengthOf(2);
        expect(comp.steps[0]).to.deep.equal({ title: 'Implement Bubble Sort', done: TestCaseState.FAIL });
        expect(comp.steps[1]).to.deep.equal({ title: 'Implement Merge Sort', done: TestCaseState.SUCCESS });
        fixture.detectChanges();
        expect(debugElement.query(By.css('.stepwizard'))).to.exist;
        expect(debugElement.queryAll(By.css('.stepwizard-circle'))).to.have.lengthOf(2);
        expect(debugElement.query(By.css('.instructions')).nativeElement.innerHTML).to.equal(problemStatementHtml);
    });
});
