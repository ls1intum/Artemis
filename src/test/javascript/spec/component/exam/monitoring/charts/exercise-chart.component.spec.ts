import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { PieChartModule } from '@swimlane/ngx-charts';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-chart.component';
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

describe('Exercise Chart Component', () => {
    let comp: ExerciseChartComponent;
    let fixture: ComponentFixture<ExerciseChartComponent>;
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

    it('should call initData on init with actions', () => {
        // GIVEN
        const lastActionByStudent = new Map();
        const action = new SwitchedExerciseAction(0);
        action.examActivityId = 1;
        lastActionByStudent.set(action.examActivityId, action);
        examActionService.cachedLastActionPerStudent.set(exam.id!, lastActionByStudent);
        comp.exam = exam;

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: `${exercises[0].title} (${exercises[0].id})`, value: 1 }]);
        expect(comp.ngxColor.domain).toEqual([getColor(0)]);
    });
});
