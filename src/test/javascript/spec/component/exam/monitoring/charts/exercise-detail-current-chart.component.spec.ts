import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { PieChartModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { createTestExercises } from '../exam-monitoring-helper';
import { GraphColors } from 'app/entities/statistics.model';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { DetailTemplateChartComponent } from 'app/exam/monitoring/charts/detail-chart/detail-template-chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExerciseDetailCurrentChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-current-chart.component';

describe('Exercise Detail Current Chart Component', () => {
    let comp: ExerciseDetailCurrentChartComponent;
    let fixture: ComponentFixture<ExerciseDetailCurrentChartComponent>;
    let examActionService: ExamActionService;

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
            imports: [ArtemisTestModule, MockModule(PieChartModule), ArtemisSharedComponentModule],
            declarations: [ExerciseDetailCurrentChartComponent, ChartTitleComponent, DetailTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailCurrentChartComponent);
                comp = fixture.componentInstance;
                comp.exam = exam;
                comp.exercise = exercises[0];
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
        comp.exam.numberOfRegisteredUsers = 1;
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: 'Current user', value: 0 } as NgxChartsSingleSeriesDataEntry, { name: 'Missing', value: 1 } as NgxChartsSingleSeriesDataEntry]);
    });

    it.each`
        input        | activity     | expect
        ${[0, 0, 0]} | ${[1, 2, 3]} | ${[3, 0]}
        ${[0, 0, 0]} | ${[1, 1, 2]} | ${[2, 1]}
        ${[0, 0, 1]} | ${[1, 2, 3]} | ${[2, 1]}
        ${[1, 1, 1]} | ${[1, 2, 3]} | ${[0, 3]}
    `('should call initData on init with actions', (param: { input: number[]; activity: number[]; expect: number[] }) => {
        // GIVEN
        comp.exam.numberOfRegisteredUsers = 3;

        const lastActionPerStudent = new Map();
        const action1 = new SwitchedExerciseAction(param.input[0]);
        action1.examActivityId = param.activity[0];
        lastActionPerStudent.set(action1.examActivityId, action1);
        const action2 = new SavedExerciseAction(true, param.input[1], param.input[1], true, false);
        action2.examActivityId = param.activity[1];
        lastActionPerStudent.set(action2.examActivityId, action2);
        const action3 = new SwitchedExerciseAction(param.input[2]);
        action3.examActivityId = param.activity[2];
        lastActionPerStudent.set(action3.examActivityId, action3);

        comp.exam = exam;
        examActionService.cachedLastActionPerStudent.set(exam.id!, lastActionPerStudent);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([
            { name: 'Current user', value: param.expect[0] } as NgxChartsSingleSeriesDataEntry,
            { name: 'Missing', value: param.expect[1] } as NgxChartsSingleSeriesDataEntry,
        ]);

        expect(comp.ngxColor.domain).toEqual([GraphColors.GREEN, GraphColors.RED]);
    });
});
