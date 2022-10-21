import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { BarChartModule } from '@swimlane/ngx-charts';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ExerciseGroupChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-group-chart.component';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ArtemisTestModule } from '../../../../test.module';
import { createTestExercises } from '../exam-monitoring-helper';

describe('Exercise Group Chart Component', () => {
    let comp: ExerciseGroupChartComponent;
    let fixture: ComponentFixture<ExerciseGroupChartComponent>;
    let examActionService: ExamActionService;

    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;
    const exercises1 = createTestExercises(2);
    const exercises2 = createTestExercises(2, 2);
    const exerciseGroup1 = new ExerciseGroup();
    exerciseGroup1.exercises = exercises1;
    exerciseGroup1.title = '1';
    const exerciseGroup2 = new ExerciseGroup();
    exerciseGroup2.exercises = exercises2;
    exerciseGroup2.title = '2';
    exam.exerciseGroups = [exerciseGroup1, exerciseGroup2];

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule), ArtemisSharedComponentModule],
            declarations: [ExerciseGroupChartComponent, ChartTitleComponent, ExerciseTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseGroupChartComponent);
                comp = fixture.componentInstance;
                examActionService = TestBed.inject(ExamActionService);
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
        input           | expect
        ${[0, 0, 0]}    | ${[3, 0]}
        ${[0, 1, 2]}    | ${[2, 1]}
        ${[2, 2, 2]}    | ${[0, 3]}
        ${[-1, -1, -1]} | ${[0, 0]}
    `('should call initData on init with actions', (param: { input: number[]; expect: number[] }) => {
        // GIVEN
        const lastActionByStudent = new Map();
        const action1 = new SwitchedExerciseAction(param.input[0]);
        action1.examActivityId = 1;
        lastActionByStudent.set(action1.examActivityId, action1);
        const action2 = new SwitchedExerciseAction(param.input[1]);
        action2.examActivityId = 2;
        lastActionByStudent.set(action2.examActivityId, action2);
        const action3 = new SwitchedExerciseAction(param.input[2]);
        action3.examActivityId = 3;
        lastActionByStudent.set(action3.examActivityId, action3);

        examActionService.cachedLastActionPerStudent.set(exam.id!, lastActionByStudent);
        comp.exam = exam;

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([
            { name: `${exerciseGroup1.title} (${exerciseGroup1.id})`, value: param.expect[0] },
            { name: `${exerciseGroup2.title} (${exerciseGroup2.id})`, value: param.expect[1] },
        ]);
        expect(comp.ngxColor.domain).toEqual([getColor(0), getColor(1)]);
    });
});
