import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../../test.module';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import dayjs from 'dayjs/esm';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/category-actions-chart.component';
import { createActions } from '../exam-monitoring-helper';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

describe('Category Actions Chart Component', () => {
    let comp: CategoryActionsChartComponent;
    let fixture: ComponentFixture<CategoryActionsChartComponent>;
    let pipe: ArtemisDatePipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxChartsModule],
            declarations: [AverageActionsChartComponent, ChartTitleComponent, ActionsChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ArtemisDatePipe }],
        })
            .compileComponents()
            .then(() => {});
        pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
        fixture = TestBed.createComponent(CategoryActionsChartComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should call initData on init without actions', () => {
        expect(comp.ngxData).toEqual([]);

        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        createActions().forEach((action) => {
            chartSeriesData.push({ name: action.type, series: [] });
        });

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual(chartSeriesData);
    });

    it('should call initData on init with actions', () => {
        const now = dayjs();
        // GIVEN
        comp.receivedExamActions = createActions().map((action) => {
            action.timestamp = now;
            return action;
        });

        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        comp.receivedExamActions.forEach((action) => {
            chartSeriesData.push({ name: action.type, series: [{ name: pipe.transform(now, 'short'), value: 1 }] });
        });

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual(chartSeriesData);
    });
});
