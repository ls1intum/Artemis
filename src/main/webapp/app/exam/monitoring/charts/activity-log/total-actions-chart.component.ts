import { Component, OnDestroy, OnInit } from '@angular/core';
import { getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './total-actions-chart.component.html',
})
export class TotalActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    readonly renderRate = 10;

    constructor(route: ActivatedRoute, examActionService: ExamActionService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examActionService, 'total-actions-chart', false, [getColor(2)]);
    }

    ngOnInit() {
        this.initSubscriptions();
        this.initRenderRate(this.renderRate);
        this.initData();
    }

    ngOnDestroy() {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        this.createChartData();
    }

    /**
     * Updates the data for the chart.
     */
    override updateData() {
        this.createChartData();
    }

    /**
     * Creates the chart data based on the provided actions.
     * @private
     */
    private createChartData() {
        const lastXTimestamps = this.getLastXTimestamps().map((timestamp) => timestamp.toString());

        const cachedExamActionsGroupedByTimestamp = this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId) ?? new Map();
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];

        let actionsPerTimestamp = new Map([...cachedExamActionsGroupedByTimestamp.entries()]);

        for (const timestamp of lastXTimestamps) {
            if (!cachedExamActionsGroupedByTimestamp.has(timestamp)) {
                actionsPerTimestamp.set(timestamp, 0);
            }
        }

        // We sort the map via keys in order to get the correct total amount of actions in each timestamp
        actionsPerTimestamp = new Map([...actionsPerTimestamp].sort((a, b) => (dayjs(a[0]).isBefore(dayjs(b[0])) ? -1 : 1)));

        let totalTimestamps = 0;
        for (const [timestamp, number] of actionsPerTimestamp.entries()) {
            totalTimestamps += number;
            if (lastXTimestamps.includes(timestamp)) {
                chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: totalTimestamps });
            }
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }
}
