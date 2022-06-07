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
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { SavedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { createTestExercises } from '../exam-monitoring-helper';
import { ExerciseSubmissionChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-submission-chart.component';
import { GraphColors } from 'app/entities/statistics.model';

describe('Exercise Submission Chart Component', () => {
    let comp: ExerciseSubmissionChartComponent;
    let fixture: ComponentFixture<ExerciseSubmissionChartComponent>;

    const exam = new Exam();
    const exercises = createTestExercises(2);
    const exerciseGroup = new ExerciseGroup();
    exerciseGroup.exercises = exercises;
    exam.exerciseGroups = [exerciseGroup];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxChartsModule, ArtemisSharedComponentModule],
            declarations: [ExerciseSubmissionChartComponent, ChartTitleComponent, ExerciseTemplateChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ArtemisDatePipe }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseSubmissionChartComponent);
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

    it.each`
        input           | activity     | expect    | color
        ${[0, 0, 0]}    | ${[1, 2, 3]} | ${[3, 0]} | ${[getColor(0), getColor(0)]}
        ${[0, 0, 0]}    | ${[1, 1, 2]} | ${[2, 0]} | ${[getColor(0), getColor(0)]}
        ${[0, 0, 1]}    | ${[1, 2, 3]} | ${[2, 1]} | ${[getColor(0), getColor(0)]}
        ${[1, 1, 1]}    | ${[1, 2, 3]} | ${[0, 3]} | ${[getColor(0), getColor(0)]}
        ${[-1, -1, -1]} | ${[1, 2, 3]} | ${[0, 0]} | ${[getColor(0), getColor(0)]}
    `('should call initData on init with actions', (param: { input: number[]; activity: number[]; expect: number[]; color: [GraphColors] }) => {
        // GIVEN
        const action1 = new SavedExerciseAction(true, param.input[0], param.input[0], false, false);
        action1.examActivityId = param.activity[0];
        const action2 = new SavedExerciseAction(true, param.input[1], param.input[1], true, false);
        action2.examActivityId = param.activity[1];
        const action3 = new SavedExerciseAction(true, param.input[2], param.input[2], false, true);
        action3.examActivityId = param.activity[2];

        comp.exam = exam;
        comp.examActions = [action1, action2, action3];

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([
            { name: exercises[0].title!, value: param.expect[0] },
            { name: exercises[1].title!, value: param.expect[1] },
        ]);
        expect(comp.ngxColor.domain).toEqual(param.color);
    });
});
