import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LineChartModule } from '@swimlane/ngx-charts';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ArtemisTestModule } from '../../../../test.module';
import { createActions, createSingleSeriesDataEntriesWithTimestamps } from '../exam-monitoring-helper';

describe('Average Actions Chart Component', () => {
    let comp: AverageActionsChartComponent;
    let fixture: ComponentFixture<AverageActionsChartComponent>;
    let pipe: ArtemisDatePipe;
    let examActionService: ExamActionService;

    // Course
    const course = new Course();
    course.id = 1;

    // Exam
    const exam = new Exam();
    exam.id = 1;

    const now = dayjs();
    let ceiledNow: dayjs.Dayjs;

    const route = { parent: { params: of({ courseId: course.id, examId: exam.id }) } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(LineChartModule), ArtemisSharedComponentModule],
            declarations: [AverageActionsChartComponent, ChartTitleComponent, ActionsChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(AverageActionsChartComponent);
                comp = fixture.componentInstance;
                examActionService = TestBed.inject(ExamActionService);
                ceiledNow = ceilDayjsSeconds(now, comp.timeStampGapInSeconds);
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    // On init
    it('should call initData on init without actions', () => {
        expect(comp.ngxData).toEqual([]);

        const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: 'actions', series }]);
    });

    it.each`
        amount
        ${1}
        ${2}
    `('should call initData on init with actions', (param: { amount: number }) => {
        // Create series
        const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);

        // GIVEN
        const length = createActions().length;
        const groupedByTimestamp = new Map();
        groupedByTimestamp.set(ceiledNow.toString(), length);
        examActionService.cachedExamActionsGroupedByTimestamp.set(exam.id!, groupedByTimestamp);

        comp.registeredStudents = param.amount;

        series.filter((data) => data.name === pipe.transform(ceiledNow, 'time', true).toString())[0].value = length / comp.registeredStudents;

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: 'actions', series }]);
    });
});
