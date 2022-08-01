import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AverageActionsChartComponent } from 'app/exam/statistics/charts/activity-log/average-actions-chart.component';
import { BarChartModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/statistics/charts/chart-title.component';
import dayjs from 'dayjs/esm';
import { TotalActionsChartComponent } from 'app/exam/statistics/charts/activity-log/total-actions-chart.component';
import { ActionsChartComponent } from 'app/exam/statistics/charts/activity-log/actions-chart.component';
import { createActions, createSingleSeriesDataEntriesWithTimestamps } from '../exam-live-statistics-helper';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ceilDayjsSeconds } from 'app/exam/statistics/charts/live-statistics-chart';
import { ExamActionService } from 'app/exam/statistics/exam-action.service';

describe('Total Actions Chart Component', () => {
    let comp: TotalActionsChartComponent;
    let fixture: ComponentFixture<TotalActionsChartComponent>;
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
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [AverageActionsChartComponent, ChartTitleComponent, ActionsChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(TotalActionsChartComponent);
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

    it('should call initData on init with actions', () => {
        // Create series
        const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);

        const length = createActions().length;
        const groupedByTimestamp = new Map();
        groupedByTimestamp.set(ceiledNow.toString(), length);
        examActionService.cachedExamActionsGroupedByTimestamp.set(exam.id!, groupedByTimestamp);

        // Insert value
        series.filter((data) => data.name === pipe.transform(ceiledNow, 'time', true).toString())[0].value = length;

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([{ name: 'actions', series }]);
    });
});
