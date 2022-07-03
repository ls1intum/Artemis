import { Component, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByType } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsMultiSeriesDataEntry, NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import cloneDeep from 'lodash-es/cloneDeep';

@Component({
    selector: 'jhi-category-actions-chart',
    templateUrl: './category-actions-chart.component.html',
})
export class CategoryActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    readonly renderRate = 10;

    actionsPerTimestamp: Map<string, Map<string, number>> = new Map();

    constructor(route: ActivatedRoute, examActionService: ExamActionService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examActionService, 'category-actions-chart', true, [getColor(0), getColor(1), getColor(2), getColor(3), getColor(4)]);
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
        const groupedByTimestamp = cloneDeep(this.examActionService.cachedExamActionsGroupedByTimestamp.get(this.examId)) ?? new Map();

        for (const [timestamp, actions] of Object.entries(groupedByTimestamp)) {
            const categories = new Map<string, number>();
            Object.keys(ExamActionType).forEach((type) => {
                categories.set(type, 0);
            });
            const groupedByType = groupActionsByType(actions);
            for (const [type, typeActions] of Object.entries(groupedByType)) {
                categories.set(type, typeActions.length);
            }
            this.actionsPerTimestamp.set(timestamp, categories);
        }

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
                const categories = new Map<string, number>();
                Object.keys(ExamActionType).forEach((type) => {
                    categories!.set(type, 0);
                });
                this.actionsPerTimestamp.set(timestamp, categories);
            }
        }

        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        const chartData: Map<string, NgxChartsSingleSeriesDataEntry[]> = new Map();

        Object.keys(ExamActionType).forEach((type) => {
            chartData.set(type, []);
        });

        for (const timestamp of lastXTimestamps) {
            const categories = this.actionsPerTimestamp.get(timestamp);
            for (const [category, amount] of categories!.entries()) {
                chartData.set(category, [...(chartData.get(category) ?? []), { name: this.artemisDatePipe.transform(timestamp, 'time', true), value: amount }]);
            }
        }

        for (const category of Object.keys(ExamActionType)) {
            chartSeriesData.push({ name: category, series: chartData.get(category)! });
            this.ngxColor.domain.push(getColor(Object.keys(ExamActionType).indexOf(category)));
        }

        // Remove actions out of timespan
        const keys = [...this.actionsPerTimestamp.keys()];
        for (const key of keys) {
            if (!lastXTimestamps.includes(key)) {
                this.actionsPerTimestamp.delete(key);
            }
        }

        this.ngxData = chartSeriesData;
    }

    override evaluateAndAddAction(examActions: ExamAction[]) {
        for (const action of examActions) {
            const key = action.ceiledTimestamp!.toString();
            let categories = this.actionsPerTimestamp.get(key);

            if (!categories) {
                categories = new Map<string, number>();
                Object.keys(ExamActionType).forEach((type) => {
                    categories!.set(type, 0);
                });
            }
            categories.set(action.type, (categories.get(action.type) ?? 0) + 1);
            this.actionsPerTimestamp.set(key, categories);
        }
    }

    filterRenderedData(examAction: ExamAction) {
        return this.filterActionsNotInTimeframe(examAction);
    }
}
