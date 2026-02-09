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

    it('should properly assign and cleanup generateHtmlSubscription when generateHtmlEvents is provided', fakeAsync(() => {
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
        const generateHtmlEvents = of(undefined);

        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        comp.exercise = exercise;
        comp.participation = participation;
        comp.generateHtmlEvents = generateHtmlEvents;
        // @ts-ignore
        comp.generateHtmlSubscription = oldSubscription;

        triggerChanges(comp, { property: 'participation', currentValue: participation, previousValue: oldParticipation, firstChange: false });
        fixture.changeDetectorRef.detectChanges();

        // @ts-ignore - the generateHtmlSubscription should be reassigned, not left as the old one
        expect(comp.generateHtmlSubscription).not.toEqual(oldSubscription);
        // @ts-ignore - verify it's actually a subscription
        expect(comp.generateHtmlSubscription).toBeInstanceOf(Subscription);
        flush();
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

    it('should NOT update markdown if the problemStatement is changed', fakeAsync(() => {
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
        // Wait for debounce (150ms) to complete
        tick(150);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(loadInitialResult).not.toHaveBeenCalled();
    }));

    it('should NOT update the markdown if there is no participation and the exercise has changed', fakeAsync(() => {
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
        // Wait for debounce (150ms) to complete
        tick(150);
        expect(comp.markdownExtensions).toHaveLength(2);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(loadInitialResult).not.toHaveBeenCalled();
    }));

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

        // Flush all pending timers (setTimeout in renderMarkdown)
        flush();

        // first test should be green (successful), second red (failed)
        const expectedUML = '@startuml\nclass Policy {\n<color:green>+configure()</color>\n<color:red>+testWithParenthesis()</color>}\n@enduml';
        expect(injectSpy).toHaveBeenCalledWith(expectedUML, `plantUml-${exercise.id}-0`);
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

/**
 * CRITICAL REGRESSION TESTS: PlantUML diagram isolation in exam mode.
 *
 * Background:
 * In exam mode, multiple ProgrammingExerciseInstructionComponent instances coexist
 * simultaneously in the DOM (hidden via [hidden], NOT destroyed). They all share
 * a single ProgrammingExercisePlantUmlExtensionWrapper singleton (providedIn: 'root').
 *
 * The bug (introduced by commit 756354018b, fixed by scoping IDs per exercise):
 * - resetIndex() was resetting plantUmlIndex to 0 before each render
 * - Multiple exercises generated the same container IDs (plantUml-0, plantUml-1, etc.)
 * - document.getElementById() returned the first match in DOM order (wrong exercise's container)
 * - Result: (1) diagram from wrong exercise shown, (2) diagram missing entirely
 *
 * The fix:
 * - Container IDs include the exercise ID: plantUml-{exerciseId}-{index}
 * - setExerciseId(exerciseId) sets the exercise scope before each render
 * - The per-diagram index comes from the array position (not mutable state)
 *
 * These tests simulate the exact exam scenario to prevent this regression.
 */
describe('ProgrammingExerciseInstructionComponent - PlantUML exam mode isolation', () => {
    let plantUmlExtension: ProgrammingExercisePlantUmlExtensionWrapper;

    // Problem statements with multiple PlantUML diagrams, simulating real exam exercises
    const exerciseA_problemStatement = 'Exercise A:\n@startuml\nclass SortAlgorithm {\n+sort()\n}\n@enduml\nMore text\n@startuml\nclass BubbleSort {\n+sort()\n}\n@enduml';
    const exerciseB_problemStatement = 'Exercise B:\n@startuml\nclass LinkedList {\n+add()\n+remove()\n}\n@enduml';
    const exerciseC_problemStatement =
        'Exercise C:\n@startuml\nclass Stack {\n+push()\n+pop()\n}\n@enduml\nMore\n@startuml\nclass Queue {\n+enqueue()\n+dequeue()\n}\n@enduml\nEven more\n@startuml\nclass PriorityQueue {\n+insert()\n}\n@enduml';

    function createExercise(id: number, problemStatement: string): ProgrammingExercise {
        return {
            id,
            course: { id: 1 },
            problemStatement,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
    }

    function createComponentInstance(): {
        comp: ProgrammingExerciseInstructionComponent;
        fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    } {
        const fixture = TestBed.createComponent(ProgrammingExerciseInstructionComponent);
        const comp = fixture.componentInstance;
        comp.personalParticipation = true;
        return { comp, fixture };
    }

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

        plantUmlExtension = TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    /**
     * Core exam scenario: 3 programming exercises, each with multiple PlantUML diagrams,
     * all rendered by separate component instances sharing the same singleton extension.
     *
     * This is the EXACT scenario that caused the bug in production exams.
     *
     * Note on call counts: The singleton's injectableElementsFoundSubject broadcasts callbacks
     * to ALL subscribed components. So when Exercise A renders, its injection callback also
     * appears in Exercise B's and C's callback arrays. This means the total number of
     * loadAndInjectPlantUml calls exceeds the "ideal" count because callbacks are executed
     * redundantly by multiple components. With exercise-scoped IDs, this is harmless because
     * each callback targets the correct container regardless of which component executes it.
     * The critical invariant is that all targeted IDs are globally unique.
     */
    it('should produce globally unique PlantUML container IDs when multiple components render different exercises', fakeAsync(() => {
        const injectSpy = jest.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        // Create 3 component instances (simulating 3 programming exercises in an exam)
        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();
        const instanceC = createComponentInstance();

        // Set up exercises with different numbers of PlantUML diagrams
        const exerciseA = createExercise(10, exerciseA_problemStatement); // 2 diagrams
        const exerciseB = createExercise(20, exerciseB_problemStatement); // 1 diagram
        const exerciseC = createExercise(30, exerciseC_problemStatement); // 3 diagrams

        // Configure each component (simulating what ngOnChanges does)
        for (const { comp, exercise } of [
            { comp: instanceA.comp, exercise: exerciseA },
            { comp: instanceB.comp, exercise: exerciseB },
            { comp: instanceC.comp, exercise: exerciseC },
        ]) {
            comp.exercise = exercise;
            comp.participation = { id: exercise.id! + 100 };
            // @ts-ignore - accessing private method for test setup
            comp.setupMarkdownSubscriptions();
        }

        // Render all 3 exercises sequentially (simulating student navigating through exam)
        instanceA.comp.updateMarkdown();
        instanceB.comp.updateMarkdown();
        instanceC.comp.updateMarkdown();

        // Flush all pending timers (setTimeout in scheduleContentInjection)
        flush();

        // Extract all container IDs that were targeted by injection calls
        const targetedIds: string[] = injectSpy.mock.calls.map((call) => call[1]);

        // CRITICAL: Exactly 6 unique IDs must be present (2 + 1 + 3 diagrams)
        const uniqueIds = new Set(targetedIds);
        expect(uniqueIds.size).toBe(6);

        // Verify the exact expected IDs for each exercise are present
        expect(uniqueIds).toContain('plantUml-10-0');
        expect(uniqueIds).toContain('plantUml-10-1');
        expect(uniqueIds).toContain('plantUml-20-0');
        expect(uniqueIds).toContain('plantUml-30-0');
        expect(uniqueIds).toContain('plantUml-30-1');
        expect(uniqueIds).toContain('plantUml-30-2');

        // Verify no ID belongs to an unexpected exercise
        for (const id of uniqueIds) {
            expect(id).toMatch(/^plantUml-(10|20|30)-\d+$/);
        }

        // Cleanup
        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
        instanceC.comp.ngOnDestroy();
    }));

    /**
     * Regression guard: exercises with the SAME number of diagrams.
     * This is the case most likely to collide if exercise scoping is broken,
     * because without scoping both exercises would generate plantUml-0, plantUml-1.
     */
    it('should NOT produce colliding IDs when two exercises have the same number of diagrams', fakeAsync(() => {
        const injectSpy = jest.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();

        // Both exercises use the same problem statement (same number of diagrams)
        const exerciseA = createExercise(10, exerciseA_problemStatement); // 2 diagrams
        const exerciseB = createExercise(20, exerciseA_problemStatement); // same 2 diagrams

        for (const { comp, exercise } of [
            { comp: instanceA.comp, exercise: exerciseA },
            { comp: instanceB.comp, exercise: exerciseB },
        ]) {
            comp.exercise = exercise;
            comp.participation = { id: exercise.id! + 100 };
            // @ts-ignore
            comp.setupMarkdownSubscriptions();
        }

        instanceA.comp.updateMarkdown();
        instanceB.comp.updateMarkdown();
        flush();

        const targetedIds: string[] = injectSpy.mock.calls.map((call) => call[1]);
        const uniqueIds = new Set(targetedIds);

        // Must have exactly 4 unique IDs (2 per exercise), despite both having same diagram count
        expect(uniqueIds.size).toBe(4);

        // Exercise A got IDs with prefix 10, Exercise B with prefix 20
        const exercise10Ids = [...uniqueIds].filter((id) => id.startsWith('plantUml-10-'));
        const exercise20Ids = [...uniqueIds].filter((id) => id.startsWith('plantUml-20-'));
        expect(exercise10Ids).toHaveLength(2);
        expect(exercise20Ids).toHaveLength(2);

        // No overlap between the two sets
        expect(exercise10Ids).toEqual(['plantUml-10-0', 'plantUml-10-1']);
        expect(exercise20Ids).toEqual(['plantUml-20-0', 'plantUml-20-1']);

        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
    }));

    /**
     * Verify that re-rendering an exercise produces the same IDs,
     * so that the new SVGs correctly replace old ones in the DOM.
     */
    it('should produce stable IDs when re-rendering an exercise after rendering others', fakeAsync(() => {
        const injectSpy = jest.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();

        const exerciseA = createExercise(10, exerciseA_problemStatement);
        const exerciseB = createExercise(20, exerciseB_problemStatement);

        for (const { comp, exercise } of [
            { comp: instanceA.comp, exercise: exerciseA },
            { comp: instanceB.comp, exercise: exerciseB },
        ]) {
            comp.exercise = exercise;
            comp.participation = { id: exercise.id! + 100 };
            // @ts-ignore
            comp.setupMarkdownSubscriptions();
        }

        // First render of exercise A
        instanceA.comp.updateMarkdown();
        flush();

        // Extract only exercise A's IDs from this render
        const firstRenderIds = new Set(injectSpy.mock.calls.map((call) => call[1]).filter((id: string) => id.startsWith('plantUml-10-')));
        expect(firstRenderIds).toEqual(new Set(['plantUml-10-0', 'plantUml-10-1']));

        injectSpy.mockClear();

        // Render exercise B (different exercise in between)
        instanceB.comp.updateMarkdown();
        flush();

        injectSpy.mockClear();

        // Re-render exercise A (e.g. triggered by theme change)
        // Invalidate cache to allow re-render
        // @ts-ignore
        instanceA.comp.lastRenderedProblemStatement = undefined;
        instanceA.comp.updateMarkdown();
        flush();

        // Extract only exercise A's IDs from the re-render
        const secondRenderIds = new Set(injectSpy.mock.calls.map((call) => call[1]).filter((id: string) => id.startsWith('plantUml-10-')));

        // Same exercise must get the same IDs so new SVGs overwrite old DOM containers
        expect(secondRenderIds).toEqual(new Set(['plantUml-10-0', 'plantUml-10-1']));

        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
    }));

    /**
     * Verify that setExerciseId is called with the correct exercise ID.
     * This is the critical contract between the component and the singleton extension.
     */
    it('should call setExerciseId with the exercise ID before each render', fakeAsync(() => {
        const setExerciseIdSpy = jest.spyOn(plantUmlExtension, 'setExerciseId');

        const instance = createComponentInstance();
        const exercise = createExercise(42, exerciseA_problemStatement);

        instance.comp.exercise = exercise;
        instance.comp.participation = { id: 142 };
        // @ts-ignore
        instance.comp.setupMarkdownSubscriptions();

        instance.comp.updateMarkdown();

        expect(setExerciseIdSpy).toHaveBeenCalledWith(42);

        instance.comp.ngOnDestroy();
    }));
});
