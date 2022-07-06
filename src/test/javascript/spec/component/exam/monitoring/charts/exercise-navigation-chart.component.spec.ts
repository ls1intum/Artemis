import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { createActions, createExamActionBasedOnType, createTestExercises } from '../exam-monitoring-helper';
import { GraphColors } from 'app/entities/statistics.model';
import { ExerciseNavigationChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-navigation-chart.component';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';

describe('Exercise Navigation Chart Component', () => {
    let comp: ExerciseNavigationChartComponent;
    let fixture: ComponentFixture<ExerciseNavigationChartComponent>;

    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;
    const exercises = createTestExercises(2);
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.exercises = exercises;
    exam.exerciseGroups = [exerciseGroup];

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule), ArtemisSharedComponentModule],
            declarations: [ExerciseNavigationChartComponent, ChartTitleComponent, ExerciseTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseNavigationChartComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    // On init
    it('should call initData on init without actions', () => {
        expect(comp.ngxData).toEqual([]);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([]);
    });

    it.each`
        input           | activity     | expect    | color
        ${[0, 0, 0]}    | ${[0, 0, 0]} | ${[1, 0]} | ${[getColor(0), getColor(0)]}
        ${[0, 0, 0]}    | ${[1, 2, 3]} | ${[3, 0]} | ${[getColor(0), getColor(0)]}
        ${[0, 0, 1]}    | ${[1, 2, 3]} | ${[2, 1]} | ${[getColor(0), getColor(0)]}
        ${[1, 1, 1]}    | ${[1, 2, 3]} | ${[0, 3]} | ${[getColor(0), getColor(0)]}
        ${[1, 1, 1]}    | ${[1, 1, 1]} | ${[0, 1]} | ${[getColor(0), getColor(0)]}
        ${[-1, -1, -1]} | ${[1, 2, 3]} | ${[0, 0]} | ${[getColor(0), getColor(0)]}
    `('should call initData on init with actions', (param: { input: number[]; activity: number[]; expect: number[]; color: [GraphColors] }) => {
        // GIVEN
        const action1 = new SwitchedExerciseAction(param.input[0]);
        action1.examActivityId = param.activity[0];
        const action2 = new SwitchedExerciseAction(param.input[1]);
        action2.examActivityId = param.activity[1];
        const action3 = new SwitchedExerciseAction(param.input[2]);
        action3.examActivityId = param.activity[2];

        comp.exam = exam;
        comp.filteredExamActions = [action1, action2, action3];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([
            { name: exercises[0].title!, value: param.expect[0] },
            { name: exercises[1].title!, value: param.expect[1] },
        ]);
        expect(comp.ngxColor.domain).toEqual(param.color);
    });

    // Evaluate and add action
    it('should evaluate and add action', () => {
        const action = createExamActionBasedOnType(ExamActionType.SWITCHED_EXERCISE);
        expect(comp.filteredExamActions).toEqual([]);

        comp.evaluateAndAddAction(action);

        const expectedMap = new Map();
        expectedMap.set(action.examActivityId, new Set([0]));

        expect(comp.filteredExamActions).toEqual([action]);
        expect(comp.navigatedToPerStudent).toEqual(expectedMap);
    });

    // Filter actions
    it.each(createActions())('should filter action', (action: ExamAction) => {
        expect(comp.filterRenderedData(action)).toBe(action.type === ExamActionType.SWITCHED_EXERCISE);
    });
});
