import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { getColor } from 'app/exam/statistics/charts/live-statistics-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/statistics/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    readonly renderRate = 10;

    constructor(route: ActivatedRoute, examActionService: ExamActionService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examActionService, 'average-actions-chart', false, [getColor(2)]);
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
        const actionsPerTimestamp = this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId) ?? new Map();

        const chartData: NgxChartsSingleSeriesDataEntry[] = [];
        for (const timestamp of lastXTimestamps) {
            const average = (actionsPerTimestamp.get(timestamp) ?? 0) / this.registeredStudents;
            chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: !isNaN(average) ? average : 0 });
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }
}
