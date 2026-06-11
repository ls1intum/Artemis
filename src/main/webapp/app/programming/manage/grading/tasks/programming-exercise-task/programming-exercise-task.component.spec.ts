import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
        expect(comp.onlyViewTestCases).toBe(true);
        expect(comp.open).toBe(true);
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
