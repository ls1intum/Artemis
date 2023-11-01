import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseGradingTasksTableComponent } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-grading-tasks-table.component';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { Observable, Subject, of } from 'rxjs';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';

describe('ProgrammingExerciseGradingTasksTableComponent', () => {
    let fixture;
    let comp: ProgrammingExerciseGradingTasksTableComponent;
    let taskService: ProgrammingExerciseTaskService;
    let taskServiceUpdateTasksStub: jest.SpyInstance;

    const gradingStatistics = {} as ProgrammingExerciseGradingStatistics;
    const exercise = { id: 1 } as ProgrammingExercise;
    const course = { id: 2 } as Course;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseGradingTasksTableComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [ProgrammingExerciseTaskService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseGradingTasksTableComponent);
                comp = fixture.componentInstance;

                comp.gradingStatisticsObservable = of(gradingStatistics);
                comp.exercise = exercise;
                comp.course = course;
                taskService = fixture.debugElement.injector.get(ProgrammingExerciseTaskService);
                taskServiceUpdateTasksStub = jest.spyOn(taskService, 'updateTasks');
            });
    });

    it('should pass parameters to the task service and configure it', () => {
        const taskServiceConfigureStub = jest.spyOn(taskService, 'configure').mockReturnValue(of([]));

        comp.ngOnInit();

        expect(taskServiceConfigureStub).toHaveBeenCalledExactlyOnceWith(exercise, course, gradingStatistics);
    });

    it('should update tasks through the service and set them in the component', () => {
        comp.tasks = [{ taskName: 'task1' }] as ProgrammingExerciseTask[];
        const updatedTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        taskServiceUpdateTasksStub.mockReturnValue(updatedTasks);

        comp.updateTasks();

        expect(taskServiceUpdateTasksStub).toHaveBeenCalledOnce();
        expect(comp.tasks).toBe(updatedTasks);
    });

    it('should pass inactive test case toggle to the task service and update the components tasks', () => {
        comp.tasks = [{ taskName: 'task1' }] as ProgrammingExerciseTask[];
        const inactiveTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        const taskServiceToggleIgnoreInactiveStub = jest.spyOn(taskService, 'toggleIgnoreInactive').mockReturnValue();
        taskServiceUpdateTasksStub.mockReturnValue(inactiveTasks);

        comp.toggleShowInactiveTestsShown();

        expect(taskServiceToggleIgnoreInactiveStub).toHaveBeenCalledOnce();
        expect(taskServiceUpdateTasksStub).toHaveBeenCalledOnce();
    });

    it('should pass saving to the service', () => {
        const subject = new Subject();
        const taskServiceSaveTestCasesStub = jest.spyOn(taskService, 'saveTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTestCase[]>);

        comp.saveTestCases();

        expect(comp.isSaving).toBeTruthy();
        expect(taskServiceSaveTestCasesStub).toHaveBeenCalledOnce();

        subject.next({});

        expect(comp.isSaving).toBeFalsy();
    });

    it('should pass reset to the task and update tasks', () => {
        const subject = new Subject();
        const taskServiceResetTestCasesStub = jest.spyOn(taskService, 'resetTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTask[]>);

        comp.resetTestCases();

        expect(comp.isSaving).toBeTruthy();
        expect(taskServiceResetTestCasesStub).toHaveBeenCalledOnce();

        subject.next({});

        expect(comp.isSaving).toBeFalsy();
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

        expect(comp.tasks).toEqual(expected);
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

        expect(comp.tasks).toEqual(expected);
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

        expect(comp.tasks).toEqual(expected);
    });
});
