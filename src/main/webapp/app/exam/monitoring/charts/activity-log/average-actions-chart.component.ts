import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import cloneDeep from 'lodash-es/cloneDeep';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    readonly renderRate = 10;

    actionsPerTimestamp: Map<string, number> = new Map();

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
        this.actionsPerTimestamp = cloneDeep(this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId)) ?? new Map();
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

        for (const timestamp of lastXTimestamps) {
            if (!this.actionsPerTimestamp.has(timestamp)) {
                this.actionsPerTimestamp.set(timestamp, 0);
            }
        }

        const chartData: NgxChartsSingleSeriesDataEntry[] = [];
        for (const timestamp of lastXTimestamps) {
            chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: this.actionsPerTimestamp.get(timestamp) ?? 0 });
        }

        // Remove actions out of timespan
        const keys = [...this.actionsPerTimestamp.keys()];
        for (const key of keys) {
            if (!lastXTimestamps.includes(key)) {
                this.actionsPerTimestamp.delete(key);
            }
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }

    override evaluateAndAddAction(examActions: ExamAction[]) {
        for (const action of examActions) {
            const key = action.ceiledTimestamp!.toString();
            this.actionsPerTimestamp.set(key, (this.actionsPerTimestamp.get(key) ?? 0) + 1);
        }
    }

    filterRenderedData(examAction: ExamAction) {
        return this.filterActionsNotInTimeframe(examAction);
    }
}
