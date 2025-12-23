import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement, VERSION } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import dayjs from 'dayjs/esm';
import { Subscription, of, throwError } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import {
    problemStatementBubbleSortNotExecutedHtml,
    problemStatementEmptySecondTask,
    problemStatementEmptySecondTaskNotExecutedHtml,
    problemStatementPlantUMLWithTest,
    problemStatementWithIds,
} from 'test/helpers/sample/problemStatement.json';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { triggerChanges } from 'test/helpers/utils/general-test.utils';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseInstructionTaskStatusComponent } from 'app/programming/shared/instructions-render/task/programming-exercise-instruction-task-status.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { MockModule } from 'ng-mocks';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExerciseInstructionComponent', () => {
    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseGradingService: ProgrammingExerciseGradingService;
    let modalService: NgbModal;
    let themeService: ThemeService;

    let subscribeForLatestResultOfParticipationStub: jest.SpyInstance;
    let openModalStub: jest.SpyInstance;
    let getLatestResultWithFeedbacks: jest.SpyInstance;

    const modalRef = { componentInstance: {} };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule)],
            declarations: [
                ProgrammingExerciseInstructionComponent,
                ProgrammingExerciseInstructionStepWizardComponent,
                ProgrammingExerciseInstructionTaskStatusComponent,
                TranslatePipeMock,
                SafeHtmlPipe,
            ],
            providers: [
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                ProgrammingExerciseInstructionService,
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ProgrammingExerciseGradingService, useValue: { getTestCases: () => of() } },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        programmingExerciseGradingService = TestBed.inject(ProgrammingExerciseGradingService);
        modalService = TestBed.inject(NgbModal);
        themeService = TestBed.inject(ThemeService);

        subscribeForLatestResultOfParticipationStub = jest.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
        openModalStub = jest.spyOn(modalService, 'open');
        getLatestResultWithFeedbacks = jest.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');

        comp.personalParticipation = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should on participation change clear old subscription for participation results set up new one', fakeAsync(() => {
        const exercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            isAtLeastTutor: true,
            problemStatement: 'lorem ipsum dolor sit amet',
        };
        const oldParticipation: Participation = { id: 1 };
        const result: Result = { id: 1 };
        const participation: Participation = { id: 2, submissions: [{ results: [result] }] };
        const oldSubscription = new Subscription();
        const getTestCasesSpy = jest.spyOn(programmingExerciseGradingService, 'getTestCases');
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        comp.exercise = exercise;
        comp.participation = participation;
        // @ts-ignore
        comp.participationSubscription = oldSubscription;

        triggerChanges(comp, { property: 'participation', currentValue: participation, previousValue: oldParticipation, firstChange: false });
        fixture.changeDetectorRef.detectChanges();

        expect(getTestCasesSpy).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true, exercise.id);
        // @ts-ignore
        expect(comp.participationSubscription).not.toEqual(oldSubscription);
        flush();
        expect(comp.isInitial).toBeTrue();
    }));

    it('should process empty problem statement and show empty state', () => {
        const result: Result = { id: 1, feedbacks: [] };
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const loadInitialResultStub = jest.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        const noInstructionsAvailableSpy = jest.spyOn(comp.onNoInstructionsAvailable, 'emit');
        comp.participation = participation;
        comp.exercise = exercise;
        comp.isInitial = true;
        comp.isLoading = false;

        fixture.detectChanges();
        triggerChanges(comp);
        // @ts-ignore
        expect(comp.problemStatement).toBeUndefined();
        // Component now processes empty problem statements to show empty state
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toBe(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        // No longer emits onNoInstructionsAvailable - shows empty state instead
        expect(noInstructionsAvailableSpy).not.toHaveBeenCalled();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
        fixture.changeDetectorRef.detectChanges();
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
        fixture.changeDetectorRef.detectChanges();
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
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledWith(participation.id);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.isInitial).toBeFalse();
        expect(comp.isLoading).toBeFalse();
    });

    // TODO check if this is an issue with the client itself here
    it('should create the steps task icons for the tasks in problem statement markdown', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ testCase: { testName: 'testMergeSort', id: 2 }, detailText: 'lorem ipsum', positive: true }],
        };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            problemStatement: problemStatementWithIds,
            showTestNamesToStudents: true,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };

        // @ts-ignore
        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).toHaveLength(2);
        expect(comp.tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Implement Bubble Sort](<testid>1</testid>)',
            taskName: 'Implement Bubble Sort',
            testIds: [1],
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](<testid>2</testid>)',
            taskName: 'Implement Merge Sort',
            testIds: [2],
        });
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        tick();
        fixture.changeDetectorRef.detectChanges();
        // TODO: make sure to exclude random numbers here that change after updates of dependencies
        const expectedHtml = problemStatementBubbleSortNotExecutedHtml.replaceAll('{{ANGULAR_VERSION}}', VERSION.full);
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(expectedHtml);

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
                feedbackFilter: [1],
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
                feedbackFilter: [2],
                result,
                taskName: 'Implement Merge Sort',
                numberOfNotExecutedTests: 0,
            } as FeedbackComponent,
        } as any);
    }));

    it('should create the steps task icons for the tasks in problem statement markdown with no inserted tests', fakeAsync(() => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            feedbacks: [{ testCase: { testName: 'testBubbleSort', id: 1 }, detailText: 'lorem ipsum', positive: true }],
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

        // @ts-ignore
        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect(comp.tasks).toHaveLength(2);
        expect(comp.tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Bubble Sort](<testid>1</testid>)',
            taskName: 'Bubble Sort',
            testIds: [1],
        });
        expect(comp.tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Merge Sort]()',
            taskName: 'Merge Sort',
            testIds: [],
        });
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        tick();
        fixture.changeDetectorRef.detectChanges();

        const expectedHtml = problemStatementEmptySecondTaskNotExecutedHtml.replaceAll('{{ANGULAR_VERSION}}', VERSION.full);
        // TODO: make sure to exclude random numbers here that change after updates of dependencies
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(expectedHtml);

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
                feedbackFilter: [1],
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
                { testCase: { id: 1, testName: 'testMethods[Policy]' }, positive: true },
                { testCase: { id: 2, testName: 'testPolicy()' }, positive: false },
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

        // @ts-ignore
        comp.problemStatement = exercise.problemStatement!;
        comp.exercise = exercise;
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        const plantUMLExtension = TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper);
        const injectSpy = jest.spyOn(plantUMLExtension as any, 'loadAndInjectPlantUml');

        comp.updateMarkdown();

        tick();

        // first test should be green (successful), second red (failed)
        const expectedUML = '@startuml\nclass Policy {\n<color:green>+configure()</color>\n<color:red>+testWithParenthesis()</color>}\n@enduml';
        expect(injectSpy).toHaveBeenCalledWith(expectedUML, 0);
    }));

    it('should update the markdown and set the correct problem statement if renderUpdatedProblemStatement is called', () => {
        const problemStatement = 'lorem ipsum';
        const updatedProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');
        // @ts-ignore
        comp.problemStatement = problemStatement;
        comp.exercise = { problemStatement: updatedProblemStatement } as ProgrammingExercise;
        comp.renderUpdatedProblemStatement();
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.exercise.problemStatement).toEqual(updatedProblemStatement);
    });

    it('should update the markdown on a theme change', () => {
        const updateMarkdownStub = jest.spyOn(comp, 'updateMarkdown');

        comp.isInitial = false;
        themeService.applyThemePreference(Theme.DARK);

        fixture.changeDetectorRef.detectChanges();

        // toObservable triggers a effect in the background on initial detectChanges
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
    });

    const verifyTask = (expectedInvocations: number, expected: NgbModalRef) => {
        expect(openModalStub).toHaveBeenCalledTimes(expectedInvocations);
        expect(openModalStub).toHaveBeenCalledWith(FeedbackComponent, { keyboard: true, size: 'lg' });
        expect(modalRef).toEqual(expected);
    };
});
