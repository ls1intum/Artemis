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

        let totalTimestamps = 0;
        const cachedExamActionsGroupedByTimestamp = this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId) ?? new Map();
        for (const [timestamp, amount] of cachedExamActionsGroupedByTimestamp) {
            if (!lastXTimestamps.includes(timestamp) && !dayjs(timestamp).isAfter(lastXTimestamps.last())) {
                totalTimestamps += amount;
            }
        }
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];

        for (const timestamp of lastXTimestamps) {
            totalTimestamps += cachedExamActionsGroupedByTimestamp.get(timestamp) ?? 0;
            if (lastXTimestamps.includes(timestamp)) {
                chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: totalTimestamps });
            }
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }
}
