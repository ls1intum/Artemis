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
import { ChartData, ChartSeriesData } from 'app/exam/monitoring/charts/monitoring-chart';
import { createActions } from './monitoring-chart.spec';
import dayjs from 'dayjs/esm';
import { TotalActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/total-actions-chart.component';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';

describe('Total Actions Chart Component', () => {
    let comp: TotalActionsChartComponent;
    let fixture: ComponentFixture<TotalActionsChartComponent>;
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
        fixture = TestBed.createComponent(TotalActionsChartComponent);
        comp = fixture.componentInstance;
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
        expect(comp.ngxData).toEqual([new ChartSeriesData('actions', [])]);
    });

    it('should call initData on init with actions', () => {
        const now = dayjs();
        // GIVEN
        comp.examActions = createActions().map((action) => {
            action.timestamp = now;
            return action;
        });

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([new ChartSeriesData('actions', [new ChartData(pipe.transform(now, 'short'), comp.examActions.length)])]);
    });
});
