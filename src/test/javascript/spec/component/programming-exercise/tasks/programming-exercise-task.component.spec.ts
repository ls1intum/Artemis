import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseTaskComponent } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task/programming-exercise-task.component';
import { Visibility } from 'app/entities/programming-exercise-test-case.model';
import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { Subject } from 'rxjs';

describe('ProgrammingExerciseTaskComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseTaskComponent>;
    let comp: ProgrammingExerciseTaskComponent;
    let taskService: ProgrammingExerciseTaskService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseTaskComponent, MockComponent(TestCasePassedBuildsChartComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [ProgrammingExerciseTaskService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseTaskComponent);
                comp = fixture.componentInstance;

                comp.openSubject = new Subject();

                taskService = fixture.debugElement.injector.get(ProgrammingExerciseTaskService);
                jest.spyOn(comp.updateTasksEvent, 'emit');
            });
    });

    afterEach(() => jest.restoreAllMocks());

    it('should handle test cases updates', () => {
        const testCase = {
            changed: false,
        };

        jest.spyOn(taskService, 'initializeTask').mockReturnValue({} as ProgrammingExerciseTask);

        comp.testUpdateHandler(testCase);

        expect(testCase.changed).toBeTrue();
        expect(comp.updateTasksEvent.emit).toHaveBeenCalled();
    });

    it('should handle task updates', () => {
        comp.task = {
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
        };
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

        expect(comp.task.testCases).toEqual(expected);
        expect(comp.updateTasksEvent.emit).toHaveBeenCalled();
    });

    it('should not show the task if there is only one', () => {
        taskService.currentTasks = [{ taskName: 'Not assigned to task', testCases: [], stats: undefined }] as ProgrammingExerciseTask[];

        comp.ngOnInit();
        expect(comp.onlyViewTestCases).toBeTrue();
        expect(comp.open).toBeTrue();
    });

    it('should show the tasks if there are more than one', () => {
        taskService.currentTasks = [
            { taskName: 'Not assigned to task', testCases: [], stats: undefined },
            { taskName: 'Task1', testCases: [], stats: undefined },
        ] as ProgrammingExerciseTask[];

        comp.ngOnInit();
        expect(comp.onlyViewTestCases).toBeFalsy();
        expect(comp.open).toBeFalsy();
    });
});
