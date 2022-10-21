import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { PieChartModule } from '@swimlane/ngx-charts';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { GraphColors } from 'app/entities/statistics.model';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ExerciseDetailNavigationChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-navigation-chart.component';
import { ExerciseDetailTemplateChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-template-chart.component';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ArtemisTestModule } from '../../../../test.module';
import { createTestExercises } from '../exam-monitoring-helper';

describe('Exercise Detail Navigation Chart Component', () => {
    let comp: ExerciseDetailNavigationChartComponent;
    let fixture: ComponentFixture<ExerciseDetailNavigationChartComponent>;
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
            declarations: [ExerciseDetailNavigationChartComponent, ChartTitleComponent, ExerciseDetailTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailNavigationChartComponent);
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
        expect(comp.ngxData).toEqual([{ name: 'Navigations', value: 0 } as NgxChartsSingleSeriesDataEntry, { name: 'Missing', value: 1 } as NgxChartsSingleSeriesDataEntry]);
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

        const navigationsPerStudent = new Map();
        const action1 = new SwitchedExerciseAction(param.input[0]);
        action1.examActivityId = param.activity[0];
        let navigations = navigationsPerStudent.get(action1.examActivityId) ?? new Set();
        navigations.add(param.input[0]);
        navigationsPerStudent.set(action1.examActivityId, navigations);
        const action2 = new SwitchedExerciseAction(param.input[1]);
        action2.examActivityId = param.activity[1];
        navigations = navigationsPerStudent.get(action2.examActivityId) ?? new Set();
        navigations.add(param.input[1]);
        navigationsPerStudent.set(action2.examActivityId, navigations);
        const action3 = new SwitchedExerciseAction(param.input[2]);
        action3.examActivityId = param.activity[2];
        navigations = navigationsPerStudent.get(action3.examActivityId) ?? new Set();
        navigations.add(param.input[2]);
        navigationsPerStudent.set(action3.examActivityId, navigations);

        comp.exam = exam;
        examActionService.cachedNavigationsPerStudent.set(exam.id!, navigationsPerStudent);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([
            { name: 'Navigations', value: param.expect[0] } as NgxChartsSingleSeriesDataEntry,
            { name: 'Missing', value: param.expect[1] } as NgxChartsSingleSeriesDataEntry,
        ]);

        expect(comp.ngxColor.domain).toEqual([GraphColors.GREEN, GraphColors.RED]);
    });
});
