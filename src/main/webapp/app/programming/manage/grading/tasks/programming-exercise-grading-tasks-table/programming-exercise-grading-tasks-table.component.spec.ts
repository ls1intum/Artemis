import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseGradingTasksTableComponent } from 'app/programming/manage/grading/tasks/programming-exercise-grading-tasks-table/programming-exercise-grading-tasks-table.component';
import { ProgrammingExerciseTaskService } from 'app/programming/manage/grading/tasks/programming-exercise-task.service';
import { Observable, Subject, of } from 'rxjs';
import { ProgrammingExerciseGradingStatistics } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTask } from 'app/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

describe('ProgrammingExerciseGradingTasksTableComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseGradingTasksTableComponent>;
    let comp: ProgrammingExerciseGradingTasksTableComponent;
    let taskService: ProgrammingExerciseTaskService;
    let taskServiceUpdateTasksStub: ReturnType<typeof vi.spyOn>;

    const gradingStatistics = {} as ProgrammingExerciseGradingStatistics;
    const exercise = { id: 1 } as ProgrammingExercise;
    const course = { id: 2 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProgrammingExerciseGradingTasksTableComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [ProgrammingExerciseTaskService, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        });
        fixture = TestBed.createComponent(ProgrammingExerciseGradingTasksTableComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('gradingStatisticsObservable', of(gradingStatistics));
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('course', course);
        taskService = TestBed.inject(ProgrammingExerciseTaskService);
        taskServiceUpdateTasksStub = vi.spyOn(taskService, 'updateTasks');
    });

    it('should pass parameters to the task service and configure it', () => {
        taskServiceUpdateTasksStub.mockReturnValue([]);
        const taskServiceConfigureStub = vi.spyOn(taskService, 'configure').mockReturnValue(of([]));

        comp.ngOnInit();

        expect(taskServiceConfigureStub).toHaveBeenCalledExactlyOnceWith(exercise, course, gradingStatistics);
    });

    it('should update tasks through the service and set them in the component', () => {
        comp.tasks.set([{ taskName: 'task1' }] as ProgrammingExerciseTask[]);
        const updatedTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        taskServiceUpdateTasksStub.mockReturnValue(updatedTasks);

        comp.updateTasks();

        expect(taskServiceUpdateTasksStub).toHaveBeenCalledOnce();
        expect(comp.tasks()).toBe(updatedTasks);
    });

    it('should pass inactive test case toggle to the task service and update the components tasks', () => {
        comp.tasks.set([{ taskName: 'task1' }] as ProgrammingExerciseTask[]);
        const inactiveTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        const taskServiceToggleIgnoreInactiveStub = vi.spyOn(taskService, 'toggleIgnoreInactive').mockReturnValue();
        taskServiceUpdateTasksStub.mockReturnValue(inactiveTasks);

        comp.toggleShowInactiveTestsShown();

        expect(taskServiceToggleIgnoreInactiveStub).toHaveBeenCalledOnce();
        expect(taskServiceUpdateTasksStub).toHaveBeenCalledOnce();
    });

    it('should pass saving to the service', () => {
        const subject = new Subject();
        const taskServiceSaveTestCasesStub = vi.spyOn(taskService, 'saveTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTestCase[]>);

        comp.saveTestCases();

        expect(comp.isSaving()).toBe(true);
        expect(taskServiceSaveTestCasesStub).toHaveBeenCalledOnce();

        subject.next({});

        expect(comp.isSaving()).toBe(false);
    });

    it('should pass reset to the task and update tasks', () => {
        const subject = new Subject();
        taskServiceUpdateTasksStub.mockReturnValue([]);
        const taskServiceResetTestCasesStub = vi.spyOn(taskService, 'resetTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTask[]>);

        comp.resetTestCases();

        expect(comp.isSaving()).toBe(true);
        expect(taskServiceResetTestCasesStub).toHaveBeenCalledOnce();

        subject.next({});

        expect(comp.isSaving()).toBe(false);
        expect(taskServiceUpdateTasksStub).toHaveBeenCalledOnce();
    });

    it('should sort by name by default', () => {
        comp.ngOnInit();
        expect(comp.currentSort?.by).toBe('name');
    });

    it('should change sort direction', () => {
        const tasks = [{ taskName: 'task1', weight: 2 }];

        taskServiceUpdateTasksStub.mockReturnValue(tasks);
        comp.updateTasks();

        comp.changeSort('weight');

        expect(comp.currentSort).toEqual({
            by: 'weight',
            descending: true,
        });

        comp.changeSort('weight');

        expect(comp.currentSort).toEqual({
            by: 'weight',
            descending: false,
        });
    });

    it('should change sort correctly', () => {
        const tasks = [
            { taskName: 'task1', weight: 2 },
            { taskName: 'task2', weight: 1 },
            { taskName: 'task3', weight: 0 },
            { taskName: 'task4', weight: 1 },
            { taskName: 'task5', weight: 2 },
        ] as ProgrammingExerciseTask[];
        const expected = [
            { taskName: 'task3', weight: 0 },
            { taskName: 'task2', weight: 1 },
            { taskName: 'task4', weight: 1 },
            { taskName: 'task1', weight: 2 },
            { taskName: 'task5', weight: 2 },
        ];

        taskServiceUpdateTasksStub.mockReturnValue(tasks);
        comp.updateTasks();

        comp.changeSort('weight');

        expect(comp.tasks()).toEqual(expected);
    });

    it('should sort test cases correctly', () => {
        const tasks = [
            { taskName: 'task1' },
            {
                taskName: 'task2',
                testCases: [
                    { testName: 'test1', weight: 2 },
                    { testName: 'test2', weight: 1 },
                    { testName: 'test3', weight: 0 },
                    { testName: 'test4', weight: 1 },
                    { testName: 'test5', weight: 2 },
                ],
            },
        ] as ProgrammingExerciseTask[];

        const expected = [
            { taskName: 'task1' },
            {
                taskName: 'task2',
                testCases: [
                    { testName: 'test3', weight: 0 },
                    { testName: 'test2', weight: 1 },
                    { testName: 'test4', weight: 1 },
                    { testName: 'test1', weight: 2 },
                    { testName: 'test5', weight: 2 },
                ],
            },
        ];

        taskServiceUpdateTasksStub.mockReturnValue(tasks);
        comp.updateTasks();

        comp.changeSort('weight');

        expect(comp.tasks()).toEqual(expected);
    });

    it('should sort task and test names correctly', () => {
        const tasks = [
            {
                taskName: 'task4',
                testCases: [{ testName: 'test3' }, { testName: 'test5' }, { testName: 'test4' }, { testName: 'test1' }, { testName: 'test2' }],
            },
            { taskName: 'task1' },
            { taskName: 'task3' },
            { taskName: 'task2' },
        ] as ProgrammingExerciseTask[];

        const expected = [
            { taskName: 'task1' },
            { taskName: 'task2' },
            { taskName: 'task3' },
            {
                taskName: 'task4',
                testCases: [{ testName: 'test1' }, { testName: 'test2' }, { testName: 'test3' }, { testName: 'test4' }, { testName: 'test5' }],
            },
        ];
        taskServiceUpdateTasksStub.mockReturnValue(tasks);
        comp.updateTasks();

        comp.changeSort('name');

        expect(comp.tasks()).toEqual(expected);
    });
    describe('rendered points', () => {
        const renderedPoints = (testName: string): string => {
            const rows = Array.from(fixture.nativeElement.querySelectorAll('.task__row')) as HTMLElement[];
            const row = rows.find((candidate) => candidate.querySelector('.task__field')?.textContent?.trim() === testName)!;
            return (row.querySelectorAll('.task__field')[1] as HTMLElement).textContent!.trim();
        };

        it('should update the points of all test cases when the weight of one test case changes', async () => {
            TestBed.inject(ProgrammingExerciseTaskService);
            fixture.componentRef.setInput('exercise', { id: 1, maxPoints: 10 } as ProgrammingExercise);
            const httpMock = TestBed.inject(HttpTestingController);
            fixture.detectChanges();
            httpMock.expectOne('api/programming/programming-exercises/1/tasks-with-unassigned-test-cases').flush([
                { taskName: 'Task 1', testCases: [{ id: 1, testName: 'testOne', weight: 1, bonusMultiplier: 1, bonusPoints: 0, active: true }] },
                { taskName: 'Task 2', testCases: [{ id: 2, testName: 'testTwo', weight: 1, bonusMultiplier: 1, bonusPoints: 0, active: true }] },
            ]);
            fixture.detectChanges();
            // both tasks are collapsed initially: expand them so that the test case rows are rendered
            comp.toggleAllTasksExpanded(true);
            await fixture.whenStable();
            fixture.detectChanges();
            expect(renderedPoints('testOne')).toBe('5P (50%)');
            expect(renderedPoints('testTwo')).toBe('5P (50%)');

            const weightInputs = fixture.nativeElement.querySelectorAll('.task__body input[type="number"]') as NodeListOf<HTMLInputElement>;
            weightInputs[0].value = '3';
            weightInputs[0].dispatchEvent(new Event('input'));
            weightInputs[0].dispatchEvent(new Event('change'));
            await fixture.whenStable();
            fixture.detectChanges();

            expect(renderedPoints('testOne')).toBe('7.5P (75%)');
            expect(renderedPoints('testTwo')).toBe('2.5P (25%)');
        });
    });
});
