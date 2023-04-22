import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ProgrammingExerciseGradingTaskComponent } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-grading-task.component';
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

describe('ProgrammingExerciseGradingTaskComponent', () => {
    let fixture;
    let comp: ProgrammingExerciseGradingTaskComponent;
    let taskService: ProgrammingExerciseTaskService;
    let taskServiceUpdateTasksStub: jest.SpyInstance;

    const gradingStatistics = {} as ProgrammingExerciseGradingStatistics;
    const exercise = { id: 1 } as ProgrammingExercise;
    const course = { id: 2 } as Course;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseGradingTaskComponent, MockPipe(ArtemisTranslatePipe), MockComponent(ButtonComponent)],
            providers: [ProgrammingExerciseTaskService, { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseGradingTaskComponent);
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

        expect(taskServiceConfigureStub).toHaveBeenCalledWith(exercise, course, gradingStatistics);
    });

    it('should update tasks through the service and set them in the component', () => {
        comp.tasks = [{ taskName: 'task1' }] as ProgrammingExerciseTask[];
        const updatedTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        taskServiceUpdateTasksStub.mockReturnValue(updatedTasks);

        comp.updateTasks();

        expect(taskServiceUpdateTasksStub).toHaveBeenCalled();
        expect(comp.tasks).toBe(updatedTasks);
    });

    it('should pass inactive test case toggle to the task service and update the components tasks', () => {
        comp.tasks = [{ taskName: 'task1' }] as ProgrammingExerciseTask[];
        const inactiveTasks = [{ taskName: 'updatedTask' }] as ProgrammingExerciseTask[];
        const taskServiceToggleIgnoreInactiveStub = jest.spyOn(taskService, 'toggleIgnoreInactive').mockReturnValue();
        taskServiceUpdateTasksStub.mockReturnValue(inactiveTasks);

        comp.toggleShowInactiveTestsShown();

        expect(taskServiceToggleIgnoreInactiveStub).toHaveBeenCalled();
        expect(taskServiceUpdateTasksStub).toHaveBeenCalled();
    });

    it('should pass saving to the service', () => {
        const subject = new Subject();
        const taskServiceSaveTestCasesStub = jest.spyOn(taskService, 'saveTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTask[]>);

        comp.saveTestCases();

        expect(comp.isSaving).toBeTruthy();
        expect(taskServiceSaveTestCasesStub).toHaveBeenCalled();

        subject.next({});

        expect(comp.isSaving).toBeFalsy();
    });

    it('should pass reset to the task and update tasks', () => {
        const subject = new Subject();
        const taskServiceResetTestCasesStub = jest.spyOn(taskService, 'resetTestCases').mockReturnValue(subject as Observable<ProgrammingExerciseTask[]>);

        comp.resetTestCases();

        expect(comp.isSaving).toBeTruthy();
        expect(taskServiceResetTestCasesStub).toHaveBeenCalled();

        subject.next({});

        expect(comp.isSaving).toBeFalsy();
        expect(taskServiceUpdateTasksStub).toHaveBeenCalled();
    });
});
