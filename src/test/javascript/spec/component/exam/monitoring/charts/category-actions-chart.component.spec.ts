import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { LineChartModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import dayjs from 'dayjs/esm';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/category-actions-chart.component';
import { createActions, createSingleSeriesDataEntriesWithTimestamps } from '../exam-monitoring-helper';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../../helpers/mocks/service/mock-websocket.service';
import { ExamAction } from 'app/entities/exam-user-activity.model';

describe('Category Actions Chart Component', () => {
    let comp: CategoryActionsChartComponent;
    let fixture: ComponentFixture<CategoryActionsChartComponent>;
    let pipe: ArtemisDatePipe;

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
            imports: [ArtemisTestModule, MockModule(LineChartModule)],
            declarations: [AverageActionsChartComponent, ChartTitleComponent, ActionsChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(CategoryActionsChartComponent);
                comp = fixture.componentInstance;
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

        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        createActions().forEach((action) => {
            chartSeriesData.push({ name: action.type, series: createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe) });
        });

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual(chartSeriesData);
    });

    it('should call initData on init with actions', () => {
        // GIVEN
        comp.filteredExamActions = createActions().map((action) => {
            action.timestamp = now;
            action.ceiledTimestamp = ceiledNow;
            return action;
        });

        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        comp.filteredExamActions.forEach((action) => {
            // Create series
            const series = createSingleSeriesDataEntriesWithTimestamps(comp.getLastXTimestamps(), pipe);
            // Insert value
            series.filter((data) => data.name === pipe.transform(ceiledNow, 'time', true).toString())[0].value = 1;
            chartSeriesData.push({ name: action.type, series });
        });

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual(chartSeriesData);
    });

    // Filter actions
    it.each(createActions())('should filter action', (action: ExamAction) => {
        action.ceiledTimestamp = dayjs().subtract(1, 'hour');

        expect(comp.filterRenderedData(action)).toBeFalse();

        action.ceiledTimestamp = ceiledNow;

        expect(comp.filterRenderedData(action)).toBeTrue();
    });
});
