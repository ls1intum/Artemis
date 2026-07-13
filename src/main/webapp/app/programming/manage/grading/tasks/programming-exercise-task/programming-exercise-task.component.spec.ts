import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ProgrammingExerciseTaskService } from 'app/programming/manage/grading/tasks/programming-exercise-task.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTask } from 'app/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTaskComponent } from 'app/programming/manage/grading/tasks/programming-exercise-task/programming-exercise-task.component';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { TestCasePassedBuildsChartComponent } from 'app/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { Subject } from 'rxjs';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Tooltip } from 'primeng/tooltip';

describe('ProgrammingExerciseTaskComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseTaskComponent>;
    let comp: ProgrammingExerciseTaskComponent;
    let taskService: ProgrammingExerciseTaskService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseTaskComponent, MockComponent(TestCasePassedBuildsChartComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [ProgrammingExerciseTaskService, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseTaskComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('openSubject', new Subject());

        taskService = TestBed.inject(ProgrammingExerciseTaskService);
        vi.spyOn(comp.updateTasksEvent, 'emit');
    });

    afterEach(() => vi.restoreAllMocks());

    it('should handle test cases updates', () => {
        const testCase = {
            changed: false,
        };

        fixture.componentRef.setInput('task', {} as ProgrammingExerciseTask);
        vi.spyOn(taskService, 'initializeTask').mockReturnValue({} as ProgrammingExerciseTask);

        comp.testUpdateHandler(testCase);

        expect(testCase.changed).toBe(true);
        expect(comp.updateTasksEvent.emit).toHaveBeenCalled();
    });

    it('should handle task updates', () => {
        fixture.componentRef.setInput('task', {
            stats: undefined,
            weight: 4,
            bonusMultiplier: 1,
            bonusPoints: 4,
            visibility: Visibility.Never,
            testCases: [
                { testName: 'test1', weight: 1 },
                { testName: 'test2', weight: 2 },
                { testName: 'test3', weight: 3 },
                { testName: 'test4', weight: 4 },
            ],
        });
        const expected = [
            {
                bonusMultiplier: 1,
                bonusPoints: 1,
                changed: true,
                testName: 'test1',
                visibility: 'NEVER',
                weight: 0.4,
            },
            {
                bonusMultiplier: 1,
                bonusPoints: 1,
                changed: true,
                testName: 'test2',
                visibility: 'NEVER',
                weight: 0.8,
            },
            {
                bonusMultiplier: 1,
                bonusPoints: 1,
                changed: true,
                testName: 'test3',
                visibility: 'NEVER',
                weight: 1.2,
            },
            {
                bonusMultiplier: 1,
                bonusPoints: 1,
                changed: true,
                testName: 'test4',
                visibility: 'NEVER',
                weight: 1.6,
            },
        ];

        comp.taskUpdateHandler();

        expect(comp.task().testCases).toEqual(expected);
        expect(comp.updateTasksEvent.emit).toHaveBeenCalled();
    });

    it('should not show the task if there is only one', () => {
        taskService.currentTasks = [{ taskName: 'Not assigned to task', testCases: [], stats: undefined }] as ProgrammingExerciseTask[];

        comp.ngOnInit();
        expect(comp.onlyViewTestCases()).toBe(true);
        expect(comp.open()).toBe(true);
    });

    it('should show the tasks if there are more than one', () => {
        taskService.currentTasks = [
            { taskName: 'Not assigned to task', testCases: [], stats: undefined },
            { taskName: 'Task1', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];

        comp.ngOnInit();
        expect(comp.onlyViewTestCases()).toBeFalsy();
        expect(comp.open()).toBeFalsy();
    });

    it('should render task name inside a task__field element', () => {
        taskService.currentTasks = [
            { taskName: 'testBubbleSort()', testCases: [], stats: undefined },
            { taskName: 'Task1', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'testBubbleSort()', testCases: [], stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        fixture.detectChanges();

        const taskFieldEl = fixture.nativeElement.querySelector('.task__field');
        expect(taskFieldEl).not.toBeNull();
        expect(taskFieldEl.textContent.trim()).toContain('testBubbleSort()');
    });

    it('should bind a pTooltip with the full task name to the task name field so it stays readable when truncated', () => {
        const longTaskName = 'testThisIsAVeryLongTaskNameThatWouldOtherwiseBeTruncatedByEllipsis()';
        taskService.currentTasks = [
            { taskName: longTaskName, testCases: [], stats: undefined },
            { taskName: 'Task1', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: longTaskName, testCases: [], stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        fixture.detectChanges();

        const taskFieldTooltip = fixture.debugElement.query(By.css('.task__field')).injector.get(Tooltip);
        expect(taskFieldTooltip.content).toBe(longTaskName);
        expect(taskFieldTooltip.showOnEllipsis).toBe(true);
    });

    it('should render test case names inside task__field elements when task is expanded', () => {
        const testCases = [
            { testName: 'testBubbleSort()', weight: 1 },
            { testName: 'testSelectionSort()', weight: 1 },
        ];
        taskService.currentTasks = [
            { taskName: 'Task1', testCases, stats: undefined },
            { taskName: 'Task2', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'Task1', testCases, stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        comp.open.set(true);
        fixture.detectChanges();

        const taskFieldEls = fixture.nativeElement.querySelectorAll('.task__field');
        const fieldTexts = (Array.from(taskFieldEls) as Element[]).map((el) => el.textContent?.trim() ?? '');
        expect(fieldTexts).toContain('testBubbleSort()');
        expect(fieldTexts).toContain('testSelectionSort()');
    });

    it('should bind a pTooltip with the full test name to each test case name field so it stays readable when truncated', () => {
        const longTestName = 'testThisIsAVeryLongTestCaseNameThatWouldOtherwiseBeTruncatedByEllipsis()';
        const testCases = [{ testName: longTestName, weight: 1 }];
        taskService.currentTasks = [{ taskName: 'Task1', testCases, stats: undefined }] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'Task1', testCases, stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        comp.open.set(true);
        fixture.detectChanges();

        const taskFieldDebugEls = fixture.debugElement.queryAll(By.css('.task__field'));
        const testNameFieldDebugEl = taskFieldDebugEls.find((debugEl) => debugEl.nativeElement.textContent?.trim() === longTestName);
        expect(testNameFieldDebugEl).not.toBeUndefined();
        const testNameFieldTooltip = testNameFieldDebugEl!.injector.get(Tooltip);
        expect(testNameFieldTooltip.content).toBe(longTestName);
        expect(testNameFieldTooltip.showOnEllipsis).toBe(true);
    });

    it('should bind a pTooltip with the full points text to the resulting points field', () => {
        taskService.currentTasks = [
            { taskName: 'Task1', testCases: [], stats: undefined },
            { taskName: 'Task2', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', {
            taskName: 'Task1',
            testCases: [],
            stats: undefined,
            resultingPoints: 12.5,
            resultingPointsPercent: 83.3,
        } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        fixture.detectChanges();

        const taskFieldDebugEls = fixture.debugElement.queryAll(By.css('.task__field'));
        const resultingPointsFieldDebugEl = taskFieldDebugEls.find((debugEl) => debugEl.nativeElement.textContent?.trim() === '12.5P (83.3%)');
        expect(resultingPointsFieldDebugEl).not.toBeUndefined();
        expect(resultingPointsFieldDebugEl!.injector.get(Tooltip).content).toBe('12.5P (83.3%)');
    });

    it('should bind a pTooltip with the full points text to each test case resulting points field when task is expanded', () => {
        const testCases = [{ testName: 'test1', weight: 1, resultingPoints: 4, resultingPointsPercent: 100 }];
        taskService.currentTasks = [{ taskName: 'Task1', testCases, stats: undefined }] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'Task1', testCases, stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        comp.open.set(true);
        fixture.detectChanges();

        const taskFieldDebugEls = fixture.debugElement.queryAll(By.css('.task__field'));
        const resultingPointsFieldDebugEl = taskFieldDebugEls.find((debugEl) => debugEl.nativeElement.textContent?.trim() === '4P (100%)');
        expect(resultingPointsFieldDebugEl).not.toBeUndefined();
        expect(resultingPointsFieldDebugEl!.injector.get(Tooltip).content).toBe('4P (100%)');
    });

    it('should position every task__field tooltip above the row so it cannot be clipped or covered by the cells to its right', () => {
        const testCases = [{ testName: 'test1', weight: 1, resultingPoints: 4, resultingPointsPercent: 100 }];
        taskService.currentTasks = [{ taskName: 'Task1', testCases, stats: undefined }] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'Task1', testCases, stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        comp.open.set(true);
        fixture.detectChanges();

        const taskFieldDebugEls = fixture.debugElement.queryAll(By.css('.task__field'));
        expect(taskFieldDebugEls.length).toBeGreaterThan(0);
        for (const debugEl of taskFieldDebugEls) {
            expect(debugEl.injector.get(Tooltip).tooltipPosition).toBe('top');
        }
    });

    it('should append every task__field tooltip to the document body instead of the cramped table row', () => {
        // Tooltip defaults appendTo to 'self', which inserts the popup right next to the truncated span:
        // trapped inside .task__field's own overflow:hidden (invisible) or squeezed into the same flex row
        // (visually covered by the next cell). appendTo="body" escapes both failure modes.
        const testCases = [{ testName: 'test1', weight: 1, resultingPoints: 4, resultingPointsPercent: 100 }];
        taskService.currentTasks = [{ taskName: 'Task1', testCases, stats: undefined }] as ProgrammingExerciseTask[];
        fixture.componentRef.setInput('task', { taskName: 'Task1', testCases, stats: undefined } as ProgrammingExerciseTask);
        fixture.componentRef.setInput('index', 0);

        comp.ngOnInit();
        comp.open.set(true);
        fixture.detectChanges();

        const taskFieldDebugEls = fixture.debugElement.queryAll(By.css('.task__field'));
        expect(taskFieldDebugEls.length).toBeGreaterThan(0);
        for (const debugEl of taskFieldDebugEls) {
            expect(debugEl.injector.get(Tooltip).appendTo()).toBe('body');
        }
    });
});
