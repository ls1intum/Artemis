import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-chart.component';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { createActions, createExamActionBasedOnType, createTestExercises } from '../exam-monitoring-helper';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { Course } from 'app/entities/course.model';

describe('Exercise Chart Component', () => {
    let comp: ExerciseChartComponent;
    let fixture: ComponentFixture<ExerciseChartComponent>;

    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;
    const exercises = createTestExercises(1);
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.exercises = exercises;
    exam.exerciseGroups = [exerciseGroup];

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxChartsModule, ArtemisSharedComponentModule],
            declarations: [ExerciseChartComponent, ChartTitleComponent, ExerciseTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseChartComponent);
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

    it('should call initData on init with actions', () => {
        // GIVEN
        const action = new SwitchedExerciseAction(0);
        action.examActivityId = 1;

        comp.exam = exam;
        comp.filteredExamActions = [action];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: exercises[0].title!, value: 1 }]);
        expect(comp.ngxColor.domain).toEqual([getColor(0)]);
    });

    it('should call initData on init with multiple actions', () => {
        // GIVEN
        const savedAction = new SavedExerciseAction(false, 0, 0, false, true);
        savedAction.examActivityId = 1;
        const action = new SwitchedExerciseAction(0);
        action.examActivityId = 1;

        comp.exam = exam;
        comp.filteredExamActions = [savedAction, action];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: exercises[0].title!, value: 1 }]);
        expect(comp.ngxColor.domain).toEqual([getColor(0)]);
    });

    // Evaluate and add action
    it('should evaluate and add action', () => {
        const action = createExamActionBasedOnType(ExamActionType.HANDED_IN_EARLY);
        expect(comp.filteredExamActions).toEqual([]);

        comp.evaluateAndAddAction(action);

        const expectedMap = new Map();
        expectedMap.set(action.examActivityId, undefined);

        expect(comp.filteredExamActions).toEqual([action]);
        expect(comp.currentExercisePerStudent).toEqual(expectedMap);
    });

    // Filter actions
    it.each(createActions())('should filter action', (action: ExamAction) => {
        expect(comp.filterRenderedData(action)).toBe(
            action.type === ExamActionType.SWITCHED_EXERCISE ||
                action.type === ExamActionType.SAVED_EXERCISE ||
                action.type === ExamActionType.ENDED_EXAM ||
                action.type === ExamActionType.HANDED_IN_EARLY,
        );
    });
});
