import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockModule } from 'ng-mocks';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ProgrammingExerciseInstructionComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: ProgrammingExerciseInstructionComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseGradingService: ProgrammingExerciseGradingService;
    let modalService: NgbModal;
    let themeService: ThemeService;

    let subscribeForLatestResultOfParticipationStub: ReturnType<typeof vi.spyOn>;
    let openModalStub: ReturnType<typeof vi.spyOn>;
    let getLatestResultWithFeedbacks: ReturnType<typeof vi.spyOn>;

    const modalRef = { componentInstance: {} };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), ProgrammingExerciseInstructionComponent],
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

        subscribeForLatestResultOfParticipationStub = vi.spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation');
        openModalStub = vi.spyOn(modalService, 'open');
        getLatestResultWithFeedbacks = vi.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback');

        fixture.componentRef.setInput('personalParticipation', true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should on participation change clear old subscription for participation results set up new one', async () => {
        const exercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            isAtLeastTutor: true,
            problemStatement: 'lorem ipsum dolor sit amet',
        };
        const result: Result = { id: 1 };
        const participation: Participation = { id: 2, submissions: [{ results: [result] }] };
        const oldSubscription = new Subscription();
        const getTestCasesSpy = vi.spyOn(programmingExerciseGradingService, 'getTestCases');
        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        // @ts-ignore
        comp.participationSubscription = oldSubscription;

        // ngOnInit fires processInputChanges on the first detectChanges.
        fixture.changeDetectorRef.detectChanges();

        expect(getTestCasesSpy).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(participation.id, true, exercise.id);
        // @ts-ignore
        expect(comp.participationSubscription).not.toEqual(oldSubscription);
        await new Promise((resolve) => setTimeout(resolve, 200));
        expect((comp as any).isInitial).toBe(true);
    });

    it('should properly assign and cleanup generateHtmlSubscription when generateHtmlEvents is provided', async () => {
        const exercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            isAtLeastTutor: true,
            problemStatement: 'lorem ipsum dolor sit amet',
        };
        const result: Result = { id: 1 };
        const participation: Participation = { id: 2, submissions: [{ results: [result] }] };
        const oldSubscription = new Subscription();
        const generateHtmlEvents = of(undefined);

        subscribeForLatestResultOfParticipationStub.mockReturnValue(of());
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('generateHtmlEvents', generateHtmlEvents);
        // @ts-ignore
        comp.generateHtmlSubscription = oldSubscription;

        // ngOnInit handles the initial processInputChanges call.
        fixture.changeDetectorRef.detectChanges();

        // @ts-ignore - the generateHtmlSubscription should be reassigned, not left as the old one
        expect(comp.generateHtmlSubscription).not.toEqual(oldSubscription);
        // @ts-ignore - verify it's actually a subscription
        expect(comp.generateHtmlSubscription).toBeInstanceOf(Subscription);
        await new Promise((resolve) => setTimeout(resolve, 200));
    });

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
        const loadInitialResultStub = vi.spyOn(comp, 'loadInitialResult').mockReturnValue(of(result));
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');
        const noInstructionsAvailableSpy = vi.spyOn(comp.onNoInstructionsAvailable, 'emit');
        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('exercise', exercise);
        (comp as any).isInitial = true;
        (comp as any).isLoading = false;

        // ngOnInit fires processInputChanges automatically.
        fixture.detectChanges();
        // @ts-ignore
        expect(comp.problemStatement).toBeUndefined();
        // Component now processes empty problem statements to show empty state
        expect(loadInitialResultStub).toHaveBeenCalledOnce();
        expect(comp.latestResult).toBe(result);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        // No longer emits onNoInstructionsAvailable - shows empty state instead
        expect(noInstructionsAvailableSpy).not.toHaveBeenCalled();
        expect((comp as any).isInitial).toBe(false);
        expect((comp as any).isLoading).toBe(false);
        fixture.changeDetectorRef.detectChanges();
        expect(debugElement.query(By.css('#programming-exercise-instructions-loading'))).toBeNull();
        expect(debugElement.query(By.css('#programming-exercise-instructions-content'))).not.toBeNull();
    });

    it('should NOT update markdown if the problemStatement is changed', async () => {
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
        // Seed participation before any change detection so the effect's initial seed pass
        // captures it — subsequent input-change effect runs see no participation change.
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');
        const loadInitialResult = vi.spyOn(comp, 'loadInitialResult');
        fixture.componentRef.setInput('exercise', { ...exercise, problemStatement: oldProblemStatement });
        (comp as any).isInitial = false;
        // Prime the seen problem statement so the next change is detected as a real edit.
        (comp as any).lastSeenProblemStatement = oldProblemStatement;
        fixture.componentRef.setInput('exercise', { ...comp.exercise(), problemStatement: newProblemStatement });
        fixture.detectChanges();
        // Wait for debounce (150ms) to complete
        await new Promise((resolve) => setTimeout(resolve, 200));
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(loadInitialResult).not.toHaveBeenCalled();
    });

    it('should NOT update the markdown if there is no participation and the exercise has changed', async () => {
        const participation: Participation = { id: 2 };
        const exercise: ProgrammingExercise = {
            id: 3,
            course: { id: 4 },
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const newProblemStatement = 'new lorem ipsum';
        // Seed participation before any change detection so the effect's initial seed pass
        // captures it; the subsequent change exercises the "exercise changed, participation didn't" path.
        fixture.componentRef.setInput('participation', participation);
        fixture.detectChanges();
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');
        const loadInitialResult = vi.spyOn(comp, 'loadInitialResult');
        (comp as any).isInitial = false;
        (comp as any).lastSeenProblemStatement = undefined;
        fixture.componentRef.setInput('exercise', { ...exercise, problemStatement: newProblemStatement });
        fixture.detectChanges();
        // Wait for debounce (150ms) to complete
        await new Promise((resolve) => setTimeout(resolve, 200));
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
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');
        getLatestResultWithFeedbacks.mockReturnValue(throwError(() => new Error('fatal error')));
        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('exercise', exercise);
        (comp as any).isInitial = true;
        (comp as any).isLoading = false;

        // ngOnInit fires processInputChanges automatically.
        fixture.detectChanges();

        expect(comp.markdownExtensions).toHaveLength(2);
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledOnce();
        // result should have been fetched with the submission as this is required to show details for it
        expect(getLatestResultWithFeedbacks).toHaveBeenCalledWith(participation.id);
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect((comp as any).isInitial).toBe(false);
        expect((comp as any).isLoading).toBe(false);
    });

    // TODO check if this is an issue with the client itself here
    it('should create the steps task icons for the tasks in problem statement markdown', async () => {
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
        fixture.componentRef.setInput('exercise', exercise);
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect((comp as any).tasks).toHaveLength(2);
        expect((comp as any).tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Implement Bubble Sort](<testid>1</testid>)',
            taskName: 'Implement Bubble Sort',
            testIds: [1],
        });
        expect((comp as any).tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Implement Merge Sort](<testid>2</testid>)',
            taskName: 'Implement Merge Sort',
            testIds: [2],
        });
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        await new Promise((resolve) => setTimeout(resolve, 10));
        fixture.changeDetectorRef.detectChanges();
        // TODO: make sure to exclude random numbers here that change after updates of dependencies
        const expectedHtml = problemStatementBubbleSortNotExecutedHtml.replaceAll('{{ANGULAR_VERSION}}', VERSION.full);
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(expectedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        expect(bubbleSortStep).not.toBeNull();
        expect(mergeSortStep).not.toBeNull();

        openModalStub.mockReturnValue(modalRef as any);

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
    });

    it('should create the steps task icons for the tasks in problem statement markdown with no inserted tests', async () => {
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
        fixture.componentRef.setInput('exercise', exercise);
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        comp.updateMarkdown();

        expect((comp as any).tasks).toHaveLength(2);
        expect((comp as any).tasks[0]).toEqual({
            id: 0,
            completeString: '[task][Bubble Sort](<testid>1</testid>)',
            taskName: 'Bubble Sort',
            testIds: [1],
        });
        expect((comp as any).tasks[1]).toEqual({
            id: 1,
            completeString: '[task][Merge Sort]()',
            taskName: 'Merge Sort',
            testIds: [],
        });
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('.stepwizard'))).not.toBeNull();
        expect(debugElement.queryAll(By.css('.btn-circle'))).toHaveLength(2);
        await new Promise((resolve) => setTimeout(resolve, 10));
        fixture.changeDetectorRef.detectChanges();

        const expectedHtml = problemStatementEmptySecondTaskNotExecutedHtml.replaceAll('{{ANGULAR_VERSION}}', VERSION.full);
        // TODO: make sure to exclude random numbers here that change after updates of dependencies
        expect(debugElement.query(By.css('.instructions__content__markdown')).nativeElement.innerHTML).toEqual(expectedHtml);

        const bubbleSortStep = debugElement.query(By.css('.stepwizard-step--success'));
        const mergeSortStep = debugElement.query(By.css('.stepwizard-step--not-executed'));
        expect(bubbleSortStep).not.toBeNull();
        expect(mergeSortStep).not.toBeNull();

        openModalStub.mockReturnValue(modalRef as any);

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
    });

    it('should create the correct colors in problem statement plantuml diagram with inserted tests', async () => {
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
        fixture.componentRef.setInput('exercise', exercise);
        comp.latestResult = result;
        // @ts-ignore
        comp.setupMarkdownSubscriptions();

        const plantUMLExtension = TestBed.inject(ProgrammingExercisePlantUmlExtensionWrapper);
        const injectSpy = vi.spyOn(plantUMLExtension as any, 'loadAndInjectPlantUml');

        comp.updateMarkdown();

        // Flush all pending timers (setTimeout in renderMarkdown)
        await new Promise((resolve) => setTimeout(resolve, 10));

        // first test should be green (successful), second red (failed)
        const expectedUML = '@startuml\nclass Policy {\n<color:green>+configure()</color>\n<color:red>+testWithParenthesis()</color>}\n@enduml';
        expect(injectSpy).toHaveBeenCalledWith(expectedUML, `plantUml-${exercise.id}-0`);
    });

    it('should update the markdown and set the correct problem statement if renderUpdatedProblemStatement is called', () => {
        const problemStatement = 'lorem ipsum';
        const updatedProblemStatement = 'new lorem ipsum';
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');
        // @ts-ignore
        comp.problemStatement = problemStatement;
        fixture.componentRef.setInput('exercise', { problemStatement: updatedProblemStatement } as ProgrammingExercise);
        comp.renderUpdatedProblemStatement();
        expect(updateMarkdownStub).toHaveBeenCalledOnce();
        expect(comp.exercise()!.problemStatement).toEqual(updatedProblemStatement);
    });

    it('should update the markdown on a theme change', () => {
        // Establish a starting theme and flush the constructor effect's initial run (skipped
        // because isInitial=true). After flipping isInitial=false, the next theme change is the
        // first run that should call updateMarkdown.
        themeService.applyThemePreference(Theme.LIGHT);
        fixture.changeDetectorRef.detectChanges();
        const updateMarkdownStub = vi.spyOn(comp, 'updateMarkdown');

        (comp as any).isInitial = false;
        themeService.applyThemePreference(Theme.DARK);
        fixture.changeDetectorRef.detectChanges();

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
 * These tests simulate the exact exam scenario to prevent regressions of the
 * per-exercise PlantUML container ID scoping fix.
 */
describe('ProgrammingExerciseInstructionComponent - PlantUML exam mode isolation', () => {
    setupTestBed({ zoneless: true });

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
        fixture.componentRef.setInput('personalParticipation', true);
        return { comp, fixture };
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), ProgrammingExerciseInstructionComponent],
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
        vi.restoreAllMocks();
    });

    it('should produce globally unique PlantUML container IDs when multiple components render different exercises', async () => {
        const injectSpy = vi.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();
        const instanceC = createComponentInstance();

        const exerciseA = createExercise(10, exerciseA_problemStatement);
        const exerciseB = createExercise(20, exerciseB_problemStatement);
        const exerciseC = createExercise(30, exerciseC_problemStatement);

        for (const { fixture, comp, exercise } of [
            { ...instanceA, exercise: exerciseA },
            { ...instanceB, exercise: exerciseB },
            { ...instanceC, exercise: exerciseC },
        ]) {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', { id: exercise.id! + 100 });
            // @ts-ignore - accessing private method for test setup
            comp.setupMarkdownSubscriptions();
        }

        instanceA.comp.updateMarkdown();
        instanceB.comp.updateMarkdown();
        instanceC.comp.updateMarkdown();

        await new Promise((resolve) => setTimeout(resolve, 10));

        const targetedIds: string[] = injectSpy.mock.calls.map((call) => call[1] as string);

        const uniqueIds = new Set(targetedIds);
        expect(uniqueIds.size).toBe(6);

        expect(uniqueIds).toContain('plantUml-10-0');
        expect(uniqueIds).toContain('plantUml-10-1');
        expect(uniqueIds).toContain('plantUml-20-0');
        expect(uniqueIds).toContain('plantUml-30-0');
        expect(uniqueIds).toContain('plantUml-30-1');
        expect(uniqueIds).toContain('plantUml-30-2');

        for (const id of uniqueIds) {
            expect(id).toMatch(/^plantUml-(10|20|30)-\d+$/);
        }

        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
        instanceC.comp.ngOnDestroy();
    });

    it('should NOT produce colliding IDs when two exercises have the same number of diagrams', async () => {
        const injectSpy = vi.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();

        const exerciseA = createExercise(10, exerciseA_problemStatement);
        const exerciseB = createExercise(20, exerciseA_problemStatement);

        for (const { fixture, comp, exercise } of [
            { ...instanceA, exercise: exerciseA },
            { ...instanceB, exercise: exerciseB },
        ]) {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', { id: exercise.id! + 100 });
            // @ts-ignore
            comp.setupMarkdownSubscriptions();
        }

        instanceA.comp.updateMarkdown();
        instanceB.comp.updateMarkdown();
        await new Promise((resolve) => setTimeout(resolve, 10));

        const targetedIds: string[] = injectSpy.mock.calls.map((call) => call[1] as string);
        const uniqueIds = new Set(targetedIds);

        expect(uniqueIds.size).toBe(4);

        const exercise10Ids = [...uniqueIds].filter((id) => id.startsWith('plantUml-10-'));
        const exercise20Ids = [...uniqueIds].filter((id) => id.startsWith('plantUml-20-'));
        expect(exercise10Ids).toHaveLength(2);
        expect(exercise20Ids).toHaveLength(2);

        expect(exercise10Ids).toEqual(['plantUml-10-0', 'plantUml-10-1']);
        expect(exercise20Ids).toEqual(['plantUml-20-0', 'plantUml-20-1']);

        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
    });

    it('should produce stable IDs when re-rendering an exercise after rendering others', async () => {
        const injectSpy = vi.spyOn(plantUmlExtension as any, 'loadAndInjectPlantUml');

        const instanceA = createComponentInstance();
        const instanceB = createComponentInstance();

        const exerciseA = createExercise(10, exerciseA_problemStatement);
        const exerciseB = createExercise(20, exerciseB_problemStatement);

        for (const { fixture, comp, exercise } of [
            { ...instanceA, exercise: exerciseA },
            { ...instanceB, exercise: exerciseB },
        ]) {
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participation', { id: exercise.id! + 100 });
            // @ts-ignore
            comp.setupMarkdownSubscriptions();
        }

        instanceA.comp.updateMarkdown();
        await new Promise((resolve) => setTimeout(resolve, 10));

        const firstRenderIds = new Set(injectSpy.mock.calls.map((call) => call[1] as string).filter((id) => (id as string).startsWith('plantUml-10-')));
        expect(firstRenderIds).toEqual(new Set(['plantUml-10-0', 'plantUml-10-1']));

        injectSpy.mockClear();

        instanceB.comp.updateMarkdown();
        await new Promise((resolve) => setTimeout(resolve, 10));

        injectSpy.mockClear();

        // @ts-ignore
        instanceA.comp.lastRenderedProblemStatement = undefined;
        instanceA.comp.updateMarkdown();
        await new Promise((resolve) => setTimeout(resolve, 10));

        const secondRenderIds = new Set(injectSpy.mock.calls.map((call) => call[1] as string).filter((id) => (id as string).startsWith('plantUml-10-')));

        expect(secondRenderIds).toEqual(new Set(['plantUml-10-0', 'plantUml-10-1']));

        instanceA.comp.ngOnDestroy();
        instanceB.comp.ngOnDestroy();
    });

    it('should call setExerciseId with the exercise ID before each render', async () => {
        const setExerciseIdSpy = vi.spyOn(plantUmlExtension, 'setExerciseId');

        const instance = createComponentInstance();
        const exercise = createExercise(42, exerciseA_problemStatement);

        instance.fixture.componentRef.setInput('exercise', exercise);
        instance.fixture.componentRef.setInput('participation', { id: 142 });
        // @ts-ignore
        instance.comp.setupMarkdownSubscriptions();

        instance.comp.updateMarkdown();
        await new Promise((resolve) => setTimeout(resolve, 10));

        expect(setExerciseIdSpy).toHaveBeenCalledWith(42);

        instance.comp.ngOnDestroy();
    });
});
