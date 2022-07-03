import { Component, OnDestroy, OnInit } from '@angular/core';
import { getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import cloneDeep from 'lodash-es/cloneDeep';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './total-actions-chart.component.html',
})
export class TotalActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    readonly renderRate = 10;

    actionsPerTimestamp: Map<string, number> = new Map();
    currentAmountOfActionsBeforeTheFirstTimeStamp = 0;

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
        this.actionsPerTimestamp = cloneDeep(this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId) ?? new Map());
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

        let totalTimestamps = this.currentAmountOfActionsBeforeTheFirstTimeStamp;
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];

        // We sort the map via keys in order to get the correct total amount of actions in each timestamp
        const sortedMap = new Map([...this.actionsPerTimestamp.entries()].sort((a, b) => (dayjs(a[0]).isBefore(dayjs(b[0])) ? -1 : 1)));

        for (const [timestamp, number] of sortedMap.entries()) {
            totalTimestamps += number;
            if (lastXTimestamps.includes(timestamp)) {
                chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: totalTimestamps });
            } else {
                this.currentAmountOfActionsBeforeTheFirstTimeStamp = totalTimestamps;
            }
        }

        // Remove actions out of timespan
        const keys = [...sortedMap.keys()];
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
}
