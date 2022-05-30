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
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import dayjs from 'dayjs/esm';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { createActions } from '../exam-monitoring-helper';

describe('Average Actions Chart Component', () => {
    let comp: AverageActionsChartComponent;
    let fixture: ComponentFixture<AverageActionsChartComponent>;
    let pipe: ArtemisDatePipe;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxChartsModule, ArtemisSharedComponentModule],
            declarations: [AverageActionsChartComponent, ChartTitleComponent, ActionsChartComponent, ArtemisDatePipe, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: ArtemisDatePipe }],
        })
            .compileComponents()
            .then(() => {
                pipe = new ArtemisDatePipe(TestBed.inject(TranslateService));
                fixture = TestBed.createComponent(AverageActionsChartComponent);
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
        expect(comp.ngxData).toEqual([new ChartSeriesData('actions', [])]);
    });

    it.each`
        amount
        ${1}
        ${2}
    `('should call initData on init with actions', (param: { amount: number }) => {
        const now = dayjs();
        // GIVEN
        comp.examActions = createActions().map((action) => {
            action.timestamp = now;
            return action;
        });
        comp.registeredStudents = param.amount;

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(comp.ngxData).toEqual([new ChartSeriesData('actions', [new ChartData(pipe.transform(now, 'short'), comp.examActions.length / comp.registeredStudents)])]);
    });
});
