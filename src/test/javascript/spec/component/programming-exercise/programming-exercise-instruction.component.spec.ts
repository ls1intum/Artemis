import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { BrowserDynamicTestingModule } from '@angular/platform-browser-dynamic/testing';
import { DebugElement } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Subject, Subscription, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockResultService } from '../../helpers/mocks/service/mock-result.service';
import { MockRepositoryFileService } from '../../helpers/mocks/service/mock-repository-file.service';
import {
    problemStatement,
    problemStatementBubbleSortFailsHtml,
    problemStatementBubbleSortNotExecutedHtml,
    problemStatementEmptySecondTask,
    problemStatementEmptySecondTaskNotExecutedHtml,
    problemStatementPlantUMLWithTest,
} from '../../helpers/sample/problemStatement.json';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/exercises/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { LocalStorageService } from 'ngx-webstorage';
import { Participation } from 'app/entities/participation/participation.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockModule } from 'ng-mocks';

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let repositoryFileService: RepositoryFileService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let modalService: NgbModal;

    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let getFileStub: jest.SpyInstance;
    let openModalStub: jest.SpyInstance;
    let getLatestResultWithFeedbacks: jest.SpyInstance;

    const modalRef = { componentInstance: {} };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule)],
            declarations: [
                ProgrammingExerciseInstructionComponent,
                ProgrammingExerciseInstructionStepWizardComponent,
                ProgrammingExerciseInstructionTaskStatusComponent,
                TranslatePipeMock,
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
            ],
        })
            .overrideModule(BrowserDynamicTestingModule, { set: {} })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                repositoryFileService = debugElement.injector.get(RepositoryFileService);
                modalService = debugElement.injector.get(NgbModal);

                subscribeForLatestResultOfParticipationStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
                openModalStub = jest.spyOn(modalService, 'open');
                getFileStub = jest.spyOn(repositoryFileService, 'get');
                getLatestResultWithFeedbacks = jest.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');

                comp.personalParticipation = true;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should on participation change clear old subscription for participation results set up new one', () => {
        const exercise: ProgrammingExercise = { id: 1, numberOfAssessmentsOfCorrectionRounds: [], secondCorrectionEnabled: false, studentAssignedTeamIdComputed: false };
        const oldParticipation: Participation = { id: 1 };
        const result: Result = { id: 1 };
        const participation: Participation = { id: 2, results: [result] };
        const oldSubscription = new Subscription();
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        comp.exercise = exercise;
        comp.participation = participation;
        comp.participationSubscription = oldSubscription;

        triggerChanges(comp, { property: 'participation', currentValue: participation, previousValue: oldParticipation, firstChange: false });
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true, exercise.id);
        expect(comp.participationSubscription).not.toEqual(oldSubscription);
        expect(comp.isInitial).toBeTrue();
    });

    it('should try to fetch README.md from assignment repository if no problemStatement was provided', () => {
        const result: Result = { id: 1, feedbacks: [] };
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
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
        expect(comp.isLoading).toBeTrue();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).not.toBeNull();
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).toBeNull();
        expect(getFileStub).toHaveBeenCalledOnce();
        expect(getFileStub).toHaveBeenCalledWith(participation.id, 'README.md');

        getFileSubject.next({ fileContent: 'lorem ipsum', fileName: 'README.md' });
        expect(comp.problemStatement).toBe('lorem ipsum');
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toEqual(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBeNull();
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBeNull();
    });

    it('should NOT try to fetch README.md from assignment repository if a problemStatement was provided', () => {
        const result: Result = { id: 1, feedbacks: [] };
        const participation: Participation = { id: 2 };
        const problemstatement = 'lorem ipsum';
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement: problemstatement,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const loadInitialResultStub = jest.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);

        expect(getFileStub).not.toHaveBeenCalled();
        expect(comp.problemStatement).toBe('lorem ipsum');
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toEqual(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBeNull();
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBeNull();
    });

    it('should emit that no instructions are available if there is neither a problemStatement provided nor a README.md can be retrieved', () => {
        const result: Result = { id: 1, feedbacks: [] };
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
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
        expect(comp.isLoading).toBeTrue();
        expect(getFileStub).toHaveBeenCalledOnce();
        expect(getFileStub).toHaveBeenCalledWith(participation.id, 'README.md');

        getFileSubject.error('fatal error');
        expect(comp.problemStatement).toBeUndefined();
        expect(loadInitialResultStub).not.toHaveBeenCalled();
        expect(comp.latestResult).toBeUndefined();
        expect(updateMarkdownStub).not.toHaveBeenCalled();
        expect(noInstructionsAvailableSpy).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
        fixture.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBeNull();
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBeNull();
    });

    it('should NOT update markdown if the problemStatement is changed', () => {
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
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
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(loadInitialResult).not.toHaveBeenCalled();
    });

    it('should NOT update the markdown if there is no participation and the exercise has changed', () => {
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const newProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        const loadInitialResult = jest.spyOn(comp, 'loadInitialResult');
        fixture.detectChanges();
        comp.exercise = { ...exercise, problemStatement: newProblemStatement };
        comp.participation = participation;
        comp.isInitial = false;
        triggerChanges(comp, { property: 'exercise', currentValue: { ...comp.exercise, problemStatement: newProblemStatement }, firstChange: false });
        expect(comp.markdownExtensions).toHaveLength(2);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(loadInitialResult).not.toHaveBeenCalled();
    });

    it('should still render the instructions if fetching the latest result fails', () => {
        const participation: Participation = { id: 2 };
        const problemstatement = 'lorem ipsum';
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement: problemstatement,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        getLatestResultWithFeedbacks.mockReturnValue(throwError(() => new Error('fatal error')));
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);

        expect(comp.markdownExtensions).toHaveLength(2);
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledOnce();
        // result should have been fetched with the submission as this is required to show details for it
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledWith(participation.id, true);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
    });

    it('should create the steps task icons for the tasks in problem statement markdown (non legacy case)', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testMergeSort', detailText: 'lorem ipsum', positive: true }],
        };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement,
            showTestNamesToStudents: true,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };

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
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](testMergeSort)',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
        });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(problemStatementBubbleSortNotExecutedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).not.toBeNull();
        expect(mergeSortStep).not.toBeNull();

        openModalStub.mockReturnValue(modalRef);

        bubbleSortStep.nativeElement.click();
        verifyTask(1, {
            componentInstance: {
                exercise,
                exerciseType: ExerciseType.PROGRAMMING,
                feedbackFilter: ['testBubbleSort'],
                result,
                taskName: 'Implement Bubble Sort',
                numberOfNotExecutedTests: 1,
            } as FeedbackComponent,
        } as any);

        mergeSortStep.nativeElement.click();
        verifyTask(2, {
            componentInstance: {
                exercise,
                exerciseType: ExerciseType.PROGRAMMING,
                feedbackFilter: ['testMergeSort'],
                result,
                taskName: 'Implement Merge Sort',
                numberOfNotExecutedTests: 0,
            } as FeedbackComponent,
        } as any);
    }));

    it('should create the steps task icons for the tasks in problem statement markdown (legacy case)', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detailText: 'lorem ipsum' }],
        };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };

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
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](testMergeSort)',
            taskName: 'Implement Merge Sort',
            tests: ['testMergeSort'],
        });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(problemStatementBubbleSortFailsHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--failed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).not.toBeNull();
        expect(mergeSortStep).not.toBeNull();

        openModalStub.mockReturnValue(modalRef);

        bubbleSortStep.nativeElement.click();
        verifyTask(1, {
            componentInstance: {
                exercise,
                exerciseType: ExerciseType.PROGRAMMING,
                feedbackFilter: ['testBubbleSort'],
                result,
                taskName: 'Implement Bubble Sort',
                numberOfNotExecutedTests: 0,
            } as FeedbackComponent,
        } as any);

        mergeSortStep.nativeElement.click();
        verifyTask(2, {
            componentInstance: {
                exercise,
                exerciseType: ExerciseType.PROGRAMMING,
                feedbackFilter: ['testMergeSort'],
                result,
                taskName: 'Implement Merge Sort',
                numberOfNotExecutedTests: 0,
            } as FeedbackComponent,
        } as any);
    }));

    it('should create the steps task icons for the tasks in problem statement markdown with no inserted tests (non legacy case)', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detailText: 'lorem ipsum', positive: true }],
        };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement: problemStatementEmptySecondTask,
            showTestNamesToStudents: true,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };

        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).toHaveLength(2);
        expect(comp.tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Bubble Sort](testBubbleSort)',
            taskName: 'Bubble Sort',
            tests: ['testBubbleSort'],
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Merge Sort]()',
            taskName: 'Merge Sort',
            tests: [],
        });
        fixture.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        tick();
        fixture.detectChanges();
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(problemStatementEmptySecondTaskNotExecutedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        expect(bubbleSortStep).not.toBeNull();
        expect(mergeSortStep).not.toBeNull();

        openModalStub.mockReturnValue(modalRef);

        bubbleSortStep.nativeElement.click();
        verifyTask(1, {
            componentInstance: {
                exercise,
                exerciseType: ExerciseType.PROGRAMMING,
                feedbackFilter: ['testBubbleSort'],
                result,
                taskName: 'Bubble Sort',
                numberOfNotExecutedTests: 0,
            } as FeedbackComponent,
        } as any);

        mergeSortStep.nativeElement.click();
        // Should not get called another time
        expect(openModalStub).toHaveBeenCalledOnce();
    }));

    it('should create the correct colors in problem statement plantuml diagram with inserted tests', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [
                { text: 'testMethods[Policy]', positive: true },
                { text: 'testPolicy()', positive: false },
            ],
        };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement: problemStatementPlantUMLWithTest,
            showTestNamesToStudents: true,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };

        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        const plantUMLExtension = TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper);
        const injectSpy = jest.spyOn(plantUMLExtension as any, 'loadAndInjectPlantUml');

        comp.updateMarkdown();

        fixture.detectChanges();
        tick();

        // first test should be green (successful), second red (failed)
        const expectedUML = '@startuml\nclass Policy {\n<color:green>+configure()</color>\n<color:red>+testWithParenthesis()</color>}\n@enduml';
        expect(injectSpy).toHaveBeenCalledWith(expectedUML, 0);
    }));

    it('should update the markdown and set the correct problem statement if renderUpdatedProblemStatement is called', () => {
        const problemStatement = 'lorem ipsum';
        const updatedProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        comp.problemStatement = problemStatement;
        comp.exercise = { problemStatement: updatedProblemStatement } as ProgrammingExercise;
        comp.renderUpdatedProblemStatement();
        expect(comp.problemStatement).toBe(updatedProblemStatement);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
    });

    const verifyTask = (expectedInvocations: number, expected: NgbModalRef) => {
        expect(openModalStub).toHaveBeenCalledTimes(expectedInvocations);
        expect(openModalStub).toHaveBeenCalledWith(FeedbackComponent, { keyboard: true, size: 'lg' });
        expect(modalRef).toEqual(expected);
    };
});
