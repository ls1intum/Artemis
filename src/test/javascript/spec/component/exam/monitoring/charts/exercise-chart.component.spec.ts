import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ChartData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-chart.component';
import { Exam } from 'app/entities/exam.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { createActions, createTestExercises } from '../exam-monitoring-helper';

describe('Exercise Chart Component', () => {
    let comp: ExerciseChartComponent;
    let fixture: ComponentFixture<ExerciseChartComponent>;

    const exam = new Exam();
    const exercises = createTestExercises(1);
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.exercises = exercises;
    exam.exerciseGroups = [exerciseGroup];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxChartsModule, ArtemisSharedComponentModule],
            declarations: [ExerciseChartComponent, ChartTitleComponent, ExerciseTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ArtemisDatePipe }],
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

    it('should call initData on init without actions', () => {
        expect(comp.ngxData).toEqual([]);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([]);
    });

    it('should call initData on init with actions', () => {
        // GIVEN
        const action = new SwitchedExerciseAction(1);
        action.examActivityId = 1;

        comp.exam = exam;
        comp.examActions = [action];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([new ChartData(exercises[0].title!, 1)]);
        expect(comp.ngxColor.domain).toEqual([getColor(0)]);
    });

    it('should call initData on init with multiple actions', () => {
        // GIVEN
        const actions = createActions();
        const action = new SwitchedExerciseAction(1);
        action.examActivityId = 1;

        comp.exam = exam;
        comp.examActions = [...actions, action];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([new ChartData(exercises[0].title!, 1)]);
        expect(comp.ngxColor.domain).toEqual([getColor(0)]);
    });
});
