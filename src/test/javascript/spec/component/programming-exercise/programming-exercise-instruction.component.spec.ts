import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement } from '@angular/core';
import dayjs from 'dayjs';
import { Observable, of, Subject, Subscription, throwError } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockRepositoryFileService } from '../../helpers/mocks/service/mock-repository-file.service';
import { problemStatement, problemStatementBubbleSortFailsHtml, problemStatementBubbleSortNotExecutedHtml } from '../../helpers/sample/problemStatement.json';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { HttpResponse } from '@angular/common/http';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { LocalStorageService } from 'ngx-webstorage';
import { Participation } from 'app/entities/participation/participation.model';
import { ExerciseHintService, IExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
// eslint-disable-next-line @typescript-eslint/tslint/config
// tslint:disable-next-line:max-line-length
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { Result } from 'app/entities/result.model';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockExerciseHintService } from '../../helpers/mocks/service/mock-exercise-hint.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let repositoryFileService: RepositoryFileService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let exerciseHintService: IExerciseHintService;
    let modalService: NgbModal;

    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getFileStub: jest.SpyInstance;
    let openModalStub: jest.SpyInstance;
    let getLatestResultWithFeedbacks: jest.SpyInstance;
    let getHintsForExerciseStub: jest.SpyInstance;

    const exerciseHints = [{ id: 1 }, { id: 2 }];

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseInstructionComponent,
                ProgrammingExerciseInstructionStepWizardComponent,
                ProgrammingExerciseInstructionTaskStatusComponent,
                TranslatePipeMock,
                MockComponent(FaIconComponent),
            ],
            providers: [
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                ProgrammingExerciseInstructionService,
                { provide: TranslateService, useClass: MockTranslateService },
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

                subscribeForLatestResultOfParticipationStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                openModalStub = jest.spyOn(modalService, 'open');
                getFileStub = jest.spyOn(repositoryFileService, 'get');
                getLatestResultWithFeedbacks = jest.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');
                getHintsForExerciseStub = jest
                    .spyOn(exerciseHintService, 'findByExerciseId')
                    .mockReturnValue(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);

                comp.personalParticipation = true;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should on participation change clear old subscription for participation results set up new one', () => {
        const exercise = { id: 1 };
        const oldParticipation = { id: 1 } as Participation;
        const result = { id: 1 };
        const participation = { id: 2, results: [result] } as Participation;
        const oldSubscription = new Subscription();
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        comp.exercise = exercise as ProgrammingExercise;
        comp.participation = participation;
        comp.participationSubscription = oldSubscription;

        triggerChanges(comp, { property: 'participation', currentValue: participation, previousValue: oldParticipation, firstChange: false });
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true, exercise.id);
        expect(comp.participationSubscription).not.toEqual(oldSubscription);
        expect(comp.isInitial).toBe(true);
        expect(getHintsForExerciseStub).toHaveBeenCalledOnce();
        expect(getHintsForExerciseStub).toHaveBeenCalledWith(exercise.id);
        expect(comp.exerciseHints).toEqual(exerciseHints);
    });

    it('should try to fetch README.md from assignment repository if no problemStatement was provided', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const getFileSubject = new Subject<{ fileContent: string; fileName: string }>();
        const loadInitialResultStub = jest.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        getFileStub.mockReturnValue(getFileSubject);
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);
        fixture.detectChanges();
        expect(comp.isLoading).toBe(true);
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).not.toBe(null);
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).toBe(null);
        expect(getFileStub).toHaveBeenCalledOnce();
        expect(getFileStub).toHaveBeenCalledWith(participation.id, 'README.md');

        getFileSubject.next({ fileContent: 'lorem ipsum', fileName: 'README.md' });
        expect(comp.problemStatement).toEqual('lorem ipsum');
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toEqual(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBe(false);
        expect(comp.isLoading).toBe(false);
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBe(null);
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBe(null);
    });

    it('should NOT try to fetch README.md from assignment repository if a problemStatement was provided', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const problemstatement = 'lorem ipsum';
        const exercise = { id: 3, course: { id: 4 }, problemStatement: problemstatement } as ProgrammingExercise;
        const loadInitialResultStub = jest.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);

        expect(getFileStub).not.toHaveBeenCalled();
        expect(comp.problemStatement).toEqual('lorem ipsum');
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toEqual(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBe(false);
        expect(comp.isLoading).toBe(false);
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBe(null);
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBe(null);
    });

    it('should emit that no instructions are available if there is neither a problemStatement provided nor a README.md can be retrieved', () => {
        const result = { id: 1, feedbacks: [] as Feedback[] } as Result;
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const getFileSubject = new Subject<{ fileContent: string; fileName: string }>();
        const loadInitialResultStub = jest.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        const noInstructionsAvailableSpy = jest.spyOn(comp.onNoInstructionsAvailable, 'emit');
        getFileStub.mockReturnValue(getFileSubject);
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);
        expect(comp.isLoading).toBe(true);
        expect(getFileStub).toHaveBeenCalledOnce();
        expect(getFileStub).toHaveBeenCalledWith(participation.id, 'README.md');

        getFileSubject.error('fatal error');
        expect(comp.problemStatement).toBe(undefined);
        expect(loadInitialResultStub).not.toHaveBeenCalled();
        expect(comp.latestResult).toBe(undefined);
        expect(updateMarkdownStub).not.toHaveBeenCalled();
        expect(noInstructionsAvailableSpy).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBe(false);
        expect(comp.isLoading).toBe(false);
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBe(null);
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBe(null);
    });

    it('should NOT update markdown if the problemStatement is changed', () => {
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const oldProblemStatement = 'lorem ipsum';
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        const loadInitialResult = jest.spyOn(comp, 'loadInitialResult');
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
        expect(updateMarkdownStub).toHaveBeenCalled();
        expect(loadInitialResult).not.toHaveBeenCalled();
    });

    it('should NOT update the markdown if there is no participation and the exercise has changed', () => {
        const participation = { id: 2 } as Participation;
        const exercise = { id: 3, course: { id: 4 } } as ProgrammingExercise;
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        const loadInitialResult = jest.spyOn(comp, 'loadInitialResult');
        fixture.detectChanges();
        comp.exercise = { ...exercise, problemStatement: newProblemStatement };
        comp.participation = participation;
        comp.isInitial = false;
        triggerChanges(comp, { property: 'exercise', currentValue: { ...comp.exercise, problemStatement: newProblemStatement }, firstChange: false });
        expect(comp.markdownExtensions).toHaveLength(2);
        expect(updateMarkdownStub).toHaveBeenCalled();
        expect(loadInitialResult).not.toHaveBeenCalled();
    });

    it('should still render the instructions if fetching the latest result fails', () => {
        const participation = { id: 2 } as Participation;
        const problemstatement = 'lorem ipsum';
        const exercise = { id: 3, course: { id: 4 }, problemStatement: problemstatement } as ProgrammingExercise;
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        getLatestResultWithFeedbacks.mockReturnValue(throwError('fatal error'));
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);

        expect(comp.markdownExtensions).toHaveLength(2);
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledOnce();
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledWith(participation.id);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBe(false);
        expect(comp.isLoading).toBe(false);
    });

    it('should create the steps task icons for the tasks in problem statement markdown (non legacy case)', fakeAsync(() => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testMergeSort', detail_text: 'lorem ipsum', positive: true }],
        } as any;
        const exercise = { id: 3, course: { id: 4 }, problemStatement, showTestNamesToStudents: true } as ProgrammingExercise;

        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).toHaveLength(2);
        expect(comp.tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Implement Bubble Sort](testBubbleSort)',
            taskName: 'Implement Bubble Sort',
            tests: ['testBubbleSort'],
            hints: [],
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](testMergeSort){33,44}',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
            hints: ['33', '44'],
        });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBe(null);
        expect(debugElement.queryAll(By.css('.stepwizard-circle'))).toHaveLength(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(problemStatementBubbleSortNotExecutedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).not.toBe(null);
        expect(mergeSortStep).not.toBe(null);

        const modalRef = { componentInstance: {} };
        openModalStub.mockReturnValue(modalRef);

        bubbleSortStep.nativeElement.click();
        mergeSortStep.nativeElement.click();

        expect(openModalStub).toHaveBeenCalledOnce();
        expect(openModalStub).toHaveBeenCalledWith(ResultDetailComponent, { keyboard: true, size: 'lg' });
        expect(modalRef).toEqual({
            componentInstance: { exerciseType: ExerciseType.PROGRAMMING, feedbackFilter: ['testBubbleSort'], result, showTestDetails: true },
        });
    }));

    it('should create the steps task icons for the tasks in problem statement markdown (legacy case)', fakeAsync(() => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum' }],
        } as any;
        const exercise = { id: 3, course: { id: 4 }, problemStatement } as ProgrammingExercise;

        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).toHaveLength(2);
        expect(comp.tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Implement Bubble Sort](testBubbleSort)',
            taskName: 'Implement Bubble Sort',
            tests: ['testBubbleSort'],
            hints: [],
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](testMergeSort){33,44}',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
            hints: ['33', '44'],
        });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBe(null);
        expect(debugElement.queryAll(By.css('.stepwizard-circle'))).toHaveLength(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(problemStatementBubbleSortFailsHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--failed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).not.toBe(null);
        expect(mergeSortStep).not.toBe(null);

        const modalRef = { componentInstance: {} };
        openModalStub.mockReturnValue(modalRef);

        bubbleSortStep.nativeElement.click();
        mergeSortStep.nativeElement.click();

        expect(openModalStub).toHaveBeenCalledOnce();
        expect(openModalStub).toHaveBeenCalledWith(ResultDetailComponent, { keyboard: true, size: 'lg' });
        expect(modalRef).toEqual({
            componentInstance: { exerciseType: ExerciseType.PROGRAMMING, feedbackFilter: ['testBubbleSort'], result, showTestDetails: false },
        });
    }));
});
